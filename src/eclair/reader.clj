(ns eclair.reader
  (:refer-clojure :exclude [read-string])
  (:require [clojure.instant :as instant]
            [clojure.string :as str]
            [instaparse.core :as insta]))

(def parser
  (insta/parser
   "expr      = list | vector | map | set | atom | symbol | tagged | unquote | splice
    <unquote> = <'~'> (var | resolve)
    splice    = <'~@'> (var | resolve)
    var       = symbol
    resolve   = <'('> <skip>? symbol (<skip> varexpr)* <skip>? <')'>
    <varexpr> = var | resolve | vector | map | set | atom | tagged
    list      = <'('> seq <')'>
    vector    = <'['> seq <']'>
    map       = <'{'> seq <'}'>
    set       = <'#{'> seq <'}'>
    <seq>     = <skip>? expr (<skip> expr)* <skip>?
    tagged    = !'#_' <'#'> sym <skip>? expr
    discard   = #'#_' <skip>? expr
    skip      = (comment | space | discard)+
    comment   = #';.*?(\n|$)'
    space     = #'[\\s,]+'
    <atom>    = string | number | bool | char | nil | keyword
    <number>  = long | bigint | double | decimal
    string    = str | bigstr
    <str>     = <'\"'> #'([^\"]|\\\\.)*' <'\"'>
    <bigstr>  = <'\"\"\"'> #'.*?[^\\\\](?=\"\"\")' <'\"\"\"'>
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

(defprotocol ExpandSplices
  (expand-element [x]))

(defrecord UnquoteSplice [value]
  ExpandSplices
  (expand-element [_] value))

(extend-type Object
  ExpandSplices
  (expand-element [x] [x]))

(defn- expand-splices [& coll]
  (mapcat expand-element coll))

(def ^:private core-transforms
  {:expr     identity
   :long     #(Long/parseLong %)
   :double   #(Double/parseDouble %)
   :bigint   #(BigInteger. %)
   :decimal  #(BigDecimal. %)
   :bool     #(= % "true")
   :nil      (constantly nil)
   :char     transform-char
   :symbol   symbol
   :qsymbol  symbol
   :keyword  keyword
   :qkeyword keyword
   :list     (comp doall expand-splices)
   :vector   (comp vec expand-splices)
   :map      (comp #(apply array-map %) expand-splices)
   :set      (comp set expand-splices)
   :splice   ->UnquoteSplice})

(def ^:private core-readers
  {'inst instant/read-instant-date
   'uuid #(java.util.UUID/fromString %)})

(def ^:private core-resolvers
  {'or #(some identity %&)
   '?  #(->> (partition 2 %&) (filter first) first second)})

(defn- make-tagged-transform [readers]
  (let [readers (merge core-readers readers)]
    (fn [tag data]
      (if-let [reader (readers (symbol tag))]
        (reader data)
        (throw (ex-info (str "No such reader for: #" tag) {:tag tag}))))))

(defn- make-string-transform [vars]
  #(-> %
       (str/replace #"\\[trn\\\"]" parse-escaped-char)
       (str/replace #"\\u\d{4}" parse-codepoint)
       (str/replace #"~\{(.*?)\}" (fn [[_ s]] (get-var vars s)))))

(defn- make-resolver-transform [resolvers]
  (let [resolvers (merge core-resolvers resolvers)]
    (fn [name & args]
      (if-let [resolver (resolvers (symbol name))]
        (apply resolver args)
        (throw (ex-info (str "No such resolver: " name) {:resolver name}))))))

(defn- make-transforms [readers resolvers vars]
  (assoc core-transforms
         :tagged  (make-tagged-transform readers)
         :string  (make-string-transform vars)
         :var     #(get vars %)
         :resolve (make-resolver-transform resolvers)))

(defn read-string
  ([s]
   (read-string s {}))
  ([s {:keys [readers resolvers vars]}]
   (insta/transform (make-transforms readers resolvers (or vars {}))
                    (parser s))))
