(ns tailrecursion.boot.tmpregistry
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.core :as core])
  (:refer-clojure :exclude [get])
  (:import java.io.File))

(def ^:dynamic *basedir* (File. "."))

;;; file stuff

(defn delete! [f]
  (let [delete (io/file f)]
    (doseq [child (.listFiles delete)] (delete! child))
    (.delete delete)))

;;; registry setup/teardown

(def registries (atom {}))

(defn ^File registry-dir []
  (io/file *basedir* ".boot" "tmp"))

(defn destroy-registry! []
  (let [dir (registry-dir)]
    (swap! registries dissoc dir)
    (delete! dir)))

(defn create-registry! []
  (destroy-registry!)
  (let [dir (registry-dir)]
    (swap! registries assoc-in [dir] {})
    (doto dir (.mkdirs))))

(defn ^File make-file [k name]
  {:pre [(or (symbol? k) (keyword? k) (string? k))]}
  (let [reg (registry-dir)
        tmp (io/file reg (munge k) name)]
    (get-in (swap! registries assoc-in [reg k] tmp) [reg k])))

;;; obtaining, deleting tmp files from a registry

(defn get [k]
  (let [reg (registry-dir)]
    (or (get-in @registries [reg k])
        (throw (IllegalArgumentException.
                (format "No temp file for key %s in registry at %s." k reg))))))

(defn exists? [k]
  (get-in @registries [(registry-dir) k]))

(defn unmk [k]
  (let [reg (registry-dir)]
    (swap! registries update-in [reg] dissoc k)
    (delete! (io/file reg (munge k)))))

(defn mk [k & [name]]
  (when (exists? k) (unmk k))
  (doto (make-file k (or name (str (gensym "file") ".tmp")))
    io/make-parents
    (.createNewFile)
    (.setLastModified (System/currentTimeMillis))))

(defn mkdir [k & [name]]
  (when (exists? k) (unmk k))
  (doto (make-file k (or name (str (gensym "dir"))))
    delete!
    (.mkdirs)))