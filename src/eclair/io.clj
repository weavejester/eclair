(ns eclair.io
  (:require [clojure.java.io :as io]
            [eclair.reader :as eclair]))

(defn env
  ([key]
   (env key nil))
  ([key default]
   (or (System/getenv key) default)))

(defn include [path]
  (eclair/read-string (slurp (io/resource path))))

(def resolvers
  {'env env, 'include include})
