(ns eclair.io
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [eclair.reader :as eclair]))

(declare load)

(declare ^:dynamic *load-path*)

(defn- resolve-path [root path]
  (str (.resolve (java.net.URI. root) path)))

(defn include [path]
  (load (resolve-path *load-path* path) eclair/*reader-options*))

(defn load
  ([path]
   (load path {}))
  ([path options]
   (let [path (str/replace-first path #"^/" "")]
     (binding [*load-path* path]
       (eclair/read-string
        (slurp (io/resource path))
        (update options :resolvers (partial merge {'include include})))))))

(defn env
  ([key]
   (env key nil))
  ([key default]
   (or (System/getenv key) default)))

(def env-resolvers
  {'env env})
