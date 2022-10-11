(ns eclair.reader
  (:refer-clojure :exclude [read-string])
  (:require [instaparse.core :as insta]))

(def parser
  (insta/parser
   "<number> = long | bigint
    long     = int
    bigint   = int <'N'>
    <int>    = #'[+-]?[0-9]+'"))

(def transforms
  {:long   #(Long/parseLong %)
   :bigint #(BigInteger. %)})

(defn read-string
  ([s]
   (read-string s {}))
  ([s opts]
   (insta/transform transforms (parser s))))
