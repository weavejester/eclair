(ns eclair.reader
  "Contains the reader for Eclair formatted strings."
  (:refer-clojure :exclude [read-string])
  (:require [clojure.instant :as instant]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [instaparse.core :as insta]))

(def ^:dynamic *reader-options*
  "Contains the options that read-string was called with. Accessible by custom
  readers and resolvers."
  nil)

(declare ^:private parse-string)

(def ^:private parser
  (insta/parser
   "root      = <skip>? (expr | baremap) <skip>?
    baremap   = expr (<skip> expr)+
    <expr>    = list | vector | map | set | atom | symbol | tagged | unquote |
                splice | nsmap | meta | quote
    unquote   = <'~'> extern
    splice    = <'~@'> extern
    extern    = var | resolve
    var       = symbol
    resolve   = <'('> symbol seq <')'>
    quote     = <'`'> expr
    meta      = <'^'> (keyword | map) <skip>? expr
    nsmap     = <'#'> keyword <skip>? map
    list      = <'('> seq <')'>
    vector    = <'['> seq <']'>
    map       = <'{'> seq <'}'>
    set       = <'#{'> seq <'}'>
    <seq>     = <skip>? (expr (<skip> expr)* <skip>?)?
    tagged    = !'#_' <'#'> sym <skip>? expr
    discard   = #'#_' <skip>? expr
    skip      = (comment | space | discard)+
    comment   = #';.*?(\n|$)'
    space     = #'[\\s,]+'
    <atom>    = string | rawstring | number | bool | char | nil | keyword
    <number>  = long | bigint | double | decimal
    string    = str | bigstr
    rawstring = <'#'> (str | bigstr)
    <str>     = <'\"'> #'(?s)([^\"]|\\\\.)*' <'\"'>
    <bigstr>  = <'\"\"\"'> #'(?s).*?[^\\\\](?=\"\"\")' <'\"\"\"'>
    symbol    = sym | sym <'/'> sym
    keyword   = <':'> (sym | sym <'/'> sym)
    <sym>     = #'[\\.+-]?[\\p{L}*!_?$%&=<>][\\p{L}\\d*!_?$%&=<>:#\\.+-]*'
    nil       = 'nil'
    bool      = 'true' | 'false'
    char      = <'\\\\'> #'[a-z0-9]+'
    long      = int
    bigint    = int <'N'>
    double    = float
    decimal   = float <'M'>
    <float>   = #'[+-]?\\d+\\.\\d+]?([eE][+-]?\\d+)?'
    <int>     = #'[+-]?\\d+'"))

(def ^:private special-chars
  {"newline"   \newline
   "space"     \space
   "tab"       \tab
   "backspace" \backspace
   "formfeed"  \formfeed
   "return"    \return})

(defn- parse-codepoint [c]
  (char (Integer/parseInt (subs c 1) 16)))

(defn- transform-char [c]
  (or (special-chars c)
      (cond
        (= (count c) 1)
        (char (.charAt c 0))
        (str/starts-with? c "u")
        (parse-codepoint c))))

(defn- transform-raw-string [s]
  (-> s
      (str/replace #"\\\"" "\"")
      (str/replace #"\\\\" "\\")))

(defprotocol ExpandSplices
  (expand-element [x]))

(defrecord UnquoteSplice [value]
  ExpandSplices
  (expand-element [_]
    (if (map? value)
      (mapcat identity value)
      value)))

(extend-protocol ExpandSplices
  Object
  (expand-element [x] (list x))
  nil
  (expand-element [_] (list nil)))

(defn- expand-splices [& coll]
  (mapcat expand-element coll))

(defn- qualify-keyword [ns k]
  (if (and (keyword? k) (not (qualified-keyword? k)))
    (keyword (name ns) (name k))
    k))

(defn- namespace-map [ns m]
  (into {} (map (fn [[k v]] [(qualify-keyword ns k) v])) m))

(def ^:private core-transforms
  {:root      identity
   :extern    identity
   :long      #(Long/parseLong %)
   :double    #(Double/parseDouble %)
   :bigint    #(BigInteger. %)
   :decimal   #(BigDecimal. %)
   :bool      #(= % "true")
   :nil       (constantly nil)
   :char      transform-char
   :symbol    symbol
   :keyword   keyword
   :rawstring transform-raw-string
   :meta      #(with-meta %2 (if (map? %1) %1 {%1 true}))
   :list      (comp doall expand-splices)
   :vector    (comp vec expand-splices)
   :map       (comp #(apply array-map %) expand-splices)
   :baremap   (comp #(apply array-map %) expand-splices)
   :nsmap     namespace-map
   :set       (comp set expand-splices)
   :unquote   identity
   :splice    ->UnquoteSplice})

(def ^:private core-readers
  {'inst    instant/read-instant-date
   'uuid    #(java.util.UUID/fromString %)
   'str     str
   'int     #(if (number? %) (long %)   (Long/parseLong (str %)))
   'float   #(if (number? %) (double %) (Double/parseDouble (str %)))
   'bool    #(if (boolean? %) % (Boolean/parseBoolean (str %)))
   'keyword #(if (keyword? %) % (keyword (str %)))
   'symbol  #(symbol (if (instance? clojure.lang.Named %) (name %) (str %)))
   're      re-pattern})

(defprotocol Reference
  (resolve-ref [ref config]))

(defrecord LocalRef [index]
  Reference
  (resolve-ref [_ config]
    (get-in config index)))

(def ^:private core-resolvers
  {'or  #(some identity %&)
   '?   #(->> (partition 2 %&) (filter first) first second)
   'ref #(->LocalRef %&)})

(defn- reference? [x]
  (satisfies? Reference x))

(defn- recursively-resolve-ref [ref config]
  (loop [val (resolve-ref ref config), previous #{}]
    (if-not (reference? val)
      val
      (if-not (contains? previous val)
        (recur (resolve-ref val config) (conj previous val))
        (throw (ex-info (str "Circular reference detected: " (:index val))
                        {:ref val}))))))

(defn- resolve-refs [config]
  (walk/postwalk #(if (reference? %) (recursively-resolve-ref % config) %)
                 config))

(defn- make-tagged-transform [readers]
  (let [readers (merge core-readers readers)]
    (fn [tag data]
      (if-let [reader (readers (symbol tag))]
        (reader data)
        (throw (ex-info (str "No such reader for: #" tag) {:tag tag}))))))

(def ^:private escape-chars
  {"t" "\t"
   "r" "\r"
   "n" "\n"
   "\\" "\\"
   "\"" "\""})

(defn- parse-escaped-char [c]
  (or (escape-chars (subs c 1))
      (throw (ex-info (str "Unsupported escape character: " c)
                      {:escape-char c}))))

(defn- make-string-transform [options]
  #(-> %
       (str/replace #"\\[trn\\\"]" parse-escaped-char)
       (str/replace #"\\u\d{4}" parse-codepoint)
       (str/replace #"~\{(.*?)\}"
                    (fn [[_ s]] (str (parse-string s options :extern))))))

(defn- make-resolver-transform [resolvers]
  (let [resolvers (merge core-resolvers resolvers)]
    (fn [name & args]
      (if-let [resolver (resolvers (symbol name))]
        (apply resolver args)
        (throw (ex-info (str "No such resolver: " name) {:resolver name}))))))

(defn- get-var [vars k]
  (some-> (or (find vars k)
              (find vars (symbol k))
              (find vars (keyword k))
              (find vars (name k)))
          val))

(defn- make-transforms [{:keys [readers resolvers vars] :as options}]
  (assoc core-transforms
         :tagged  (make-tagged-transform readers)
         :string  (make-string-transform options)
         :var     #(get-var vars %)
         :resolve (make-resolver-transform resolvers)))

(declare unquote-in-resolve unquote-nested-externs)

(defn- make-resolve-element [[_ sym & body]]
  `[:resolve ~sym ~@(map unquote-in-resolve body)])

(defn- unquote-in-resolve [tree]
  (if (vector? tree)
    (case (first tree)
      :symbol [:var tree]
      :list   (make-resolve-element tree)
      :quote  (unquote-nested-externs (second tree))
      `[~(first tree) ~@(map unquote-in-resolve (rest tree))])
    tree))

(defn- unquote-nested-externs [tree]
  (if (vector? tree)
    (case (first tree)
      :resolve (make-resolve-element tree)
      :quote   (unquote-nested-externs (second tree))
      `[~(first tree) ~@(map unquote-nested-externs (rest tree))])
    tree))

(defn- parse-string [s options start]
  (binding [*reader-options* options]
    (->> (parser s :start start)
         (unquote-nested-externs)
         (insta/transform (make-transforms options))
         (resolve-refs))))

(defn read-string
  "Reads an Eclair formatted string and return the resulting data structure.
  By default this is a pure function with no side effects.

  Accepts the following options:
    :vars      - a map of variables
    :resolvers - a map of resolver functions
    :readers   - a map of reader functions"
  ([s]
   (read-string s {}))
  ([s options]
   (parse-string s options :root)))
