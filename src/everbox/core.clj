(ns ^{:doc "(def everbox (combine evernote dropbox))"
      :author "xumingming"}
  everbox.core
  (:use [evernote.core])
  (:use [everbox.log])  
  (:require [clj-yaml.core :as yaml])
  (:require [clojure.set :as set])
  (:import [java.io File])
  (:import [java.util Date])
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

(defn list-local-notes [notebook-name]
  (let [notebook-path (get-notebook-path notebook-name)
        note-files (.listFiles (File. notebook-path))
        note-names (map #(.getName %) note-files)]
    note-names))

(defn list-cache-notes [metadata notebook-guid]
  (let [cache-notes (get-in @metadata [notebook-guid :notes])
        cache-notes (map #(:name %) cache-notes)]
    cache-notes))

(defn compute-need-to-create-notes [metadata notebook]
  (let [local-notes (list-local-notes (:name notebook))
        cache-notes (list-cache-notes metadata (:guid notebook))
        need-to-create-notes (set/difference local-notes cache-notes)]
    need-to-create-notes))

(defn compute-need-to-delete-notes [metadata notebook]
  (let [local-notes (list-local-notes (:name notebook))
        cache-notes (list-cache-notes metadata (:guid notebook))
        need-to-delete-notes (set/difference cache-notes local-notes)]
    need-to-delete-notes))

(defn get-cached-notes-by-names [metadata notebook-guid names]
  (let [notebook (@metadata notebook-guid)
        notes (filter #(contains? (set names) (:name %)) (:notes notebook))]
    notes))

(defmacro with-note-store [[note-store-sym] & body]
  `(let [~note-store-sym (get-note-store ~USER-STORE-URL ~DEV-TOKEN)]
     ~@body))

(defn update-metadata [metadata]
  (spit METADATA-FILE-PATH (str metadata)))

(defn read-metadata []
  (atom (read-string (slurp METADATA-FILE-PATH))))

(defn setup []
  "Fully retrieves the contents from evernote."
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
            (mkfile note-path (:updated-at note)))))
      (update-metadata @notebooks-atom))))

(defn handle-notes-creation [notebooks notebook]
  (let [notebook-name (:name notebook)
        notebook-guid (:guid notebook)
        need-to-create-notes (compute-need-to-create-notes notebooks notebook)]
    (with-note-store [note-store]
      (doseq [note-name need-to-create-notes
              :let [note-path (get-note-path notebook-name note-name)
                    note-content (slurp note-path)
                    note (create-note note-store note-name note-content)]]
        ;; update the metadata
        (swap! notebooks assoc-in [notebook-guid :notes (:guid note)] note)))))

(defn handle-notes-deletion [notebooks notebook]
  (let [notebook-name (:name notebook)
        notebook-guid (:guid notebook)
        need-to-delete-notes (compute-need-to-delete-notes notebooks notebook)
        need-to-delete-notes (get-cached-notes-by-names need-to-delete-notes)]
    (with-note-store [note-store]
      (doseq [note need-to-delete-notes
              :let [note-name (:name note)
                    note-guid (:guid note)
                    note-path (get-note-path notebook-name note-name)
                    note-content (slurp note-path)
                    note (delete-note note-store note-guid)]]
        ;; TODO update the metadata
        (swap! notebooks assoc-in [notebook-guid :notes (:guid note)] note)))))

(defn handle-notes-updates [notebooks notebook]
  (let [notebook-name (:name notebook)
        notebook-guid (:guid notebook)]
    (doseq [[note-guid note] (:notes notebook)
            :let [note-name (:title note)
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
        ))))

(defn local-sync []
  (while true
    (log-message "Checking...")
    (let [notebooks (read-metadata)]
      (doseq [[notebook-guid notebook] @notebooks
              :let [notebook-name (:name notebook)]]
        ;; handle notes creation
        (handle-notes-creation notebooks notebook)
        
        ;; TODO handle notes deletion
        (handle-notes-deletion notebooks notebook)
        
        ;; handle notes updates
        (handle-notes-updates notebooks notebook))
      (update-metadata @notebooks))
    ;; TODO make the sync interval configable
    (Thread/sleep 5000)))

(defn -main [action]
  (condp = action
    "setup" (setup)
    "sync" (local-sync)))