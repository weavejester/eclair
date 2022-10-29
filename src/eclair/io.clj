(ns eclair.io
  "Functions for reading Eclair data from I/O sources."
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [eclair.reader :as eclair]))

(declare load)

(def ^:dynamic *load-path*
  "Contains the path of the current loaded resource. May be used in custom
  resolvers or readers."
  nil)

(defn- resolve-path [root path]
  (str (.resolve (java.net.URI. root) path)))

(defn include
  "A resolver that takes a path to an Eclair resource on the classpath, and
  returns the parsed data. The path is relative to the file being parsed,
  but a root path may be specified by beginning the path with a '/'. The
  standard '..' path is also supported.

  Included by default in 'load'."
  [path]
  (load (resolve-path *load-path* path) eclair/*reader-options*))

(defn load
  "Load an Eclair-formatted resource file from the classpath. Takes the same
  options as eclair.reader/read-string, but adds an additional 'include'
  resolver that can be used to split a configuration over several files."
  ([path]
   (load path {}))
  ([path options]
   (let [path (str/replace-first path #"^/" "")]
     (binding [*load-path* path]
       (eclair/read-string
        (slurp (io/resource path))
        (update options :resolvers (partial merge {'include include})))))))

(defn env
  "A resolver that returns the value of an environment variable. If called
  with two arguments, the second will be a default if the variable is not
  set."
  ([key]
   (env key nil))
  ([key default]
   (or (System/getenv key) default)))

(def env-resolvers
  "Resolvers to pull data from the process environment."
  {'env env})
