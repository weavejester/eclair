(ns eclair.io
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [eclair.reader :as eclair]))

(defn env
  ([key]
   (env key nil))
  ([key default]
   (or (System/getenv key) default)))

(declare load)

(defn include [path]
  (load path))

(defn load
  ([path]
   (load path {}))
  ([path options]
   (eclair/read-string
    (slurp (io/resource path))
    (merge {:resolvers {'include include}} options))))

(def resolvers
  {'env env, 'include include})
