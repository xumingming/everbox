(ns everbox.core
  (:use [everbox.evernote])
  (:use [everbox.log])  
  (:require [clj-yaml.core :as yaml])
  (:import [java.io File])
  (:import [java.util.zip CRC32])
  (:import [java.util Date])
  (:import [com.google.common.io Files])
  (:gen-class))

(def CONFIG-FILE-PATH (str (System/getProperty "user.home") "/.everbox/everbox.yaml"))
(def METADATA-FILE-PATH (str (System/getProperty "user.home") "/.everbox/metadata"))
(defn read-config []
  (let [config-str (slurp CONFIG-FILE-PATH)
        config (yaml/parse-string config-str false)]
    config))

(doseq [[name value] (read-config)]
  (let [new-name (.toUpperCase name)]
    (eval
     `(def ~(symbol new-name) ~value))))

(defn mkdir [path]
  (.mkdirs (File. path)))

(defn mkfile [path last-modified]
  (doto (File. path)
    (.createNewFile)
    (.setLastModified last-modified)))

(defn get-notebook-path [notebook-name]
  (str EVERBOX-ROOT "/" notebook-name))

(defn get-note-path [notebook-name note-name]
  (str (get-notebook-path notebook-name) "/" note-name))

(defn get-note-local-updated-at [notebook-name note-name]
  (.lastModified (File. (get-note-path notebook-name note-name))))

(defmacro with-note-store [[note-store-sym] & body]
  `(let [~note-store-sym (get-note-store ~USER-STORE-URL ~DEV-TOKEN)]
     ~@body))

(defn gen-file-hash [file-path]
  (let [file (File. file-path)
        crc32 (CRC32.)
        checksum (Files/getChecksum file crc32)]
    checksum))

(defn update-metadata [metadata]
  (spit METADATA-FILE-PATH (str metadata)))

(defn read-metadata []
  (atom (read-string (slurp METADATA-FILE-PATH))))

(defn full-fetch []
  "Retrieves the contents from evernote."
  (with-note-store [note-store]
    (let [notebooks (list-notebooks note-store)
          notebooks-atom (atom {})]
      (doseq [notebook notebooks
              :let [notebook-name (:name notebook)
                    notebook-guid (:guid notebook)
                    notebook-path (get-notebook-path notebook-name)]]
        (swap! notebooks-atom assoc notebook-guid notebook)
        (swap! notebooks-atom assoc-in [notebook-guid :notes] {})
        (log-message "Creating new folder: " notebook-path)
        (mkdir (get-notebook-path notebook-name))
        ;; retrieves the notes for the notebook
        (let [notes (list-notebook-notes note-store notebook-guid)]
          (doseq [note notes
                  :let [note-title (:title note)
                        note-content (get-note-content note-store (:guid note))
                        note-path (get-note-path notebook-name note-title)]]
            (swap! notebooks-atom assoc-in [notebook-guid :notes (:guid note)] note)
            (log-message "Creating file: " note-path)
            (spit note-path note-content)
            (mkfile note-path (:updated-at note))
            (swap! notebooks-atom assoc-in [notebook-guid :notes (:guid note) :hash] (gen-file-hash note-path)))))
      (log-message @notebooks-atom)
      (update-metadata @notebooks-atom)
      (log-message "metadata:"))))

(defn local-sync []
  (while true
    (log-message "Checking...")
    (let [notebooks (read-metadata)]
      (doseq [[_ notebook] @notebooks
              :let [notebook-name (:name notebook)
                    notebook-guid (:guid notebook)]]
        (doseq [[_ note] (:notes notebook)
                :let [note-name (:title note)
                      note-guid (:guid note)
                      note-updated-at (:updated-at note)
                      note-local-updated-at (get-note-local-updated-at notebook-name note-name)
                      note-path (get-note-path notebook-name note-name)]]
          ;; note updated
          (when-not (= note-updated-at note-local-updated-at)
            (log-message "note: " note-path " changed! origin:" (Date. note-updated-at)
                         ", new: " (Date. note-local-updated-at))
            ;; update note
            (with-note-store [note-store]
              (update-note note-store (assoc note :content (slurp note-path))))
            ;; upate the metadata
            (swap! notebooks assoc-in [notebook-guid :notes note-guid :updated-at] note-local-updated-at)
            )
          ;; note created
          ;; note deleted
          ))
      (update-metadata @notebooks))
    (Thread/sleep 5000)))

(defn -main [action]
  (condp = action
    "setup" (full-fetch)
    "sync" (local-sync)))