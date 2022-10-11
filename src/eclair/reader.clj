(ns eclair.reader
  (:refer-clojure :exclude [read-string])
  (:require [instaparse.core :as insta]))

(def parser
  (insta/parser
   "<number> = long | bigint | double | decimal
    long     = int
    bigint   = int <'N'>
    double   = float
    decimal  = float <'M'>
    <float>  = #'[+-]?[0-9]+(\\.[0-9+])?([eE][+-]?[0-9]+)?'
    <int>    = #'[+-]?[0-9]+'"))

(def transforms
  {:long    #(Long/parseLong %)
   :double  #(Double/parseDouble %)
   :bigint  #(BigInteger. %)
   :decimal #(BigDecimal. %)})

(defn read-string
  ([s]
   (read-string s {}))
  ([s opts]
   (insta/transform transforms (parser s))))
