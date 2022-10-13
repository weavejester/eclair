(ns eclair.reader
  (:refer-clojure :exclude [read-string])
  (:require [clojure.instant :as instant]
            [clojure.string :as str]
            [instaparse.core :as insta]))

(def parser
  (insta/parser
   "expr      = list | vector | map | set | atom | tagged
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

(defn- transform-string [s]
  (-> s
      (str/replace #"\\[trn\\\"]" parse-escaped-char)
      (str/replace #"\\u\d{4}" parse-codepoint)))

(def ^:private core-transforms
  {:expr     identity
   :long     #(Long/parseLong %)
   :double   #(Double/parseDouble %)
   :bigint   #(BigInteger. %)
   :decimal  #(BigDecimal. %)
   :bool     #(= % "true")
   :nil      (constantly nil)
   :char     transform-char
   :string   transform-string
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

(defn- make-tagged-transform [readers]
  (let [readers (merge core-readers readers)]
    (fn [tag data]
      (if-let [reader (readers (symbol tag))]
        (reader data)
        (throw (ex-info (str "Cannot find reader for: #" tag) {:tag tag}))))))

(defn- make-transforms [readers]
  (assoc core-transforms :tagged (make-tagged-transform readers)))

(defn read-string
  ([s]
   (read-string s {}))
  ([s {:keys [readers]}]
   (insta/transform (make-transforms readers) (parser s))))
