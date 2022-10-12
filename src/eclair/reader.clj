(ns eclair.reader
  (:refer-clojure :exclude [read-string])
  (:require [clojure.string :as str]
            [instaparse.core :as insta]))

(def parser
  (insta/parser
   "<expr>    = list | vector | map | set | atom
    list      = <'('> seq <')'>
    vector    = <'['> seq <']'>
    map       = <'{'> seq <'}'>
    set       = <'#{'> seq <'}'>
    <seq>     = <space>? (expr (<space> expr)*) <space>?
    space     = #'[\\s,]+'
    <atom>    = string | number | bool | char | nil | symlike
    <symlike> = symbol | qsymbol | keyword | qkeyword
    <number>  = long | bigint | double | decimal
    string    = <'\"'> #'([^\"]|\\\\.)*' <'\"'>
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

(def special-chars
  {"newline"   \newline
   "space"     \space
   "tab"       \tab
   "backspace" \backspace
   "formfeed"  \formfeed
   "return"    \return})

(defn- transform-char [c]
  (or (special-chars c)
      (cond
        (= (count c) 1)
        (char (.charAt c 0))
        (str/starts-with? c "u")
        (char (Integer/parseInt (subs c 1) 16)))))

(def transforms
  {:long     #(Long/parseLong %)
   :double   #(Double/parseDouble %)
   :bigint   #(BigInteger. %)
   :decimal  #(BigDecimal. %)
   :bool     #(= % "true")
   :nil      (constantly nil)
   :char     transform-char
   :string   identity
   :symbol   symbol
   :qsymbol  symbol
   :keyword  keyword
   :qkeyword keyword
   :list     list
   :vector   vector
   :map      array-map
   :set      #(set %&)})

(defn read-string
  ([s]
   (read-string s {}))
  ([s opts]
   (insta/transform transforms (parser s))))
