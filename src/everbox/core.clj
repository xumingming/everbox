(ns everbox.core
  (:use [everbox.evernote])
  (:use [everbox.log])  
  (:require [clj-yaml.core :as yaml])
  (:import [java.io File])
  (:gen-class))

(def CONFIG-FILE-PATH (str (System/getProperty "user.home") "/.everbox.yaml"))
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

(defn mkfile [path]
  (.createNewFile (File. path)))

(defn get-notebook-path [notebook-name]
  (str EVERBOX-ROOT "/" notebook-name))

(defn get-note-path [notebook-name note-name]
  (str (get-notebook-path notebook-name) "/" note-name))

(defmacro with-note-store [[note-store-sym] & body]
  `(let [~note-store-sym (get-note-store ~USER-STORE-URL ~DEV-TOKEN)]
     ~@body))

(defn ever-sync []
  "Retrieves the contents from evernote."
  (with-note-store [note-store]
    (let [notebooks (list-notebooks note-store)]
      (doseq [notebook notebooks
              :let [notebook-name (:name notebook)
                    notebook-guid (:guid notebook)
                    notebook-path (get-notebook-path notebook-name)]]
        (log-message "Creating new folder: " notebook-path)
        (mkdir (get-notebook-path notebook-name))
        ;; retrieves the notes for the notebook
        (let [notes (list-notebook-notes note-store notebook-guid)]
          (doseq [note notes
                  :let [note-title (:title note)
                        note-content (get-note-content note-store (:guid note))
                        note-path (get-note-path notebook-name note-title)]]
            (log-message "Creating file: " note-path)
            (mkfile note-path)
            (spit note-path note-content)))))))

(defn -main []
  (ever-sync))