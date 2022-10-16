(ns eclair.reader
  (:refer-clojure :exclude [read-string])
  (:require [clojure.instant :as instant]
            [clojure.string :as str]
            [instaparse.core :as insta]))

(def parser
  (insta/parser
   "expr      = list | vector | map | set | atom | tagged | var
    <var>     = varsimple | varopt | varchoice
    varsimple = <'~'> symbol
    varopt    = <'~'> vector
    varchoice = <'~{'> seq <'}'>
    list      = <'('> seq <')'>
    vector    = <'['> seq <']'>
    map       = <'{'> seq <'}'>
    set       = <'#{'> seq <'}'>
    <seq>     = <skip>? (expr (<skip> expr)*) <skip>?
    tagged    = !'#_' <'#'> sym <skip>? expr
    discard   = #'#_' <skip>? expr
    skip      = (comment | space | discard)+
    comment   = #';.*?(\n|$)'
    space     = #'[\\s,]+'
    <atom>    = string | number | bool | char | nil | symlike
    <symlike> = symbol | qsymbol | keyword | qkeyword
    <number>  = long | bigint | double | decimal
    string    = str | bigstr
    <str>     = <'\"'> #'([^\"]|\\\\.)*' <'\"'>
    <bigstr>  = <'\"\"\"'> #'.*?[^\\\\](?=\"\"\")' <'\"\"\"'>
    symbol    = sym
    qsymbol   = sym <'/'> sym
    keyword   = <':'> sym
    qkeyword  = <':'> sym <'/'> sym
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
   :list     list
   :vector   vector
   :map      array-map
   :set      #(set %&)})

(def ^:private core-readers
  {'inst instant/read-instant-date
   'uuid #(java.util.UUID/fromString %)})

(defn- get-var [vars name]
  (if-let [[_ v] (find vars (symbol name))]
    v
    (throw (ex-info (str "No such var: " name) {:var name}))))

(defn- get-var-or-literal [vars x]
  (if (symbol? x) (vars x) x))

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

(defn- make-choice-transform [vars]
  (fn [& choices]
    (when (odd? (count choices))
      (throw (IllegalArgumentException.
              (str "No value supplied for key: " (last choices)))))
    (->> (partition 2 choices)
         (filter (fn [[k _]] (get-var vars k)))
         (first)
         (second))))

(defn- make-transforms [readers vars]
  (assoc core-transforms
         :tagged    (make-tagged-transform readers)
         :string    (make-string-transform vars)
         :varsimple #(get-var vars %)
         :varopt    #(some (partial get-var-or-literal vars) %)
         :varchoice (make-choice-transform vars)))

(defn read-string
  ([s]
   (read-string s {}))
  ([s {:keys [readers vars]}]
   (insta/transform (make-transforms readers vars)
                    (parser s))))
