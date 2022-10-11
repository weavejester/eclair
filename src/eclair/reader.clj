(ns eclair.reader
  (:refer-clojure :exclude [read-string])
  (:require [clojure.string :as str]
            [instaparse.core :as insta]))

(def parser
  (insta/parser
   "<number> = long | bigint | double | decimal | bool | nil | char
    nil      = 'nil'
    bool     = 'true' | 'false'
    char     = <'\\\\'> #'[a-z0-9]+'
    long     = int
    bigint   = int <'N'>
    double   = float
    decimal  = float <'M'>
    <float>  = #'[+-]?[0-9]+(\\.[0-9+])?([eE][+-]?[0-9]+)?'
    <int>    = #'[+-]?[0-9]+'"))

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
  {:long    #(Long/parseLong %)
   :double  #(Double/parseDouble %)
   :bigint  #(BigInteger. %)
   :decimal #(BigDecimal. %)
   :bool    #(= % "true")
   :nil     (constantly nil)
   :char    transform-char})

(defn read-string
  ([s]
   (read-string s {}))
  ([s opts]
   (insta/transform transforms (parser s))))
