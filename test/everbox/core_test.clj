(ns everbox.core-test
  (:use clojure.test
        everbox.core
        evernote.core))

(deftest test-handle-notes-updates
  (let [note1 {:guid "note1"
               :title "note-title"
               :content "note-content"
               :updated-at 1000}
        notebook1 {:guid "notebook1"
                   :name "notebook-name"
                   :notes {"note1" note1}}
        notebooks (atom {"notebook1" notebook1})]
    (with-redefs [get-note-local-updated-at (fn [& _] 2000)
                  update-note (fn [& _] nil)
                  get-local-note-content (fn [& _] "updated-content")]
      (handle-notes-updates notebooks notebook1)
      (let [nb (@notebooks "notebook1")
            updated-note ((:notes nb) "note1")]
        (is (= 2000 (:updated-at updated-note)))
        (is (= "updated-content" (:content updated-note)))))))

(deftest test-handle-notes-creation
  (let [note1 {:guid "note1"
               :title "note-title"
               :content "note-content"
               :updated-at 1000}
        notebook1 {:guid "notebook1"
                   :name "notebook-name"
                   :notes {}}
        notebooks (atom {"notebook1" notebook1})]
    (with-redefs [compute-need-to-create-notes (fn [& _] ["note1"])
                  create-note (fn [& _] note1)
                  get-local-note-content (fn [& _] "local-content")]
      (handle-notes-creation notebooks notebook1)
      (let [nb (@notebooks "notebook1")
            created-note ((:notes nb) "note1")]
        (is (= 1 (count (:notes nb)))))
      (println @notebooks))))


(deftest test-list-cache-notes
  (let [note1 {:guid "note1"
               :title "note-title"
               :content "note-content"
               :updated-at 1000}
        notebook1 {:guid "notebook1"
                   :name "notebook-name"
                   :notes {"note1" note1}}
        notebooks (atom {"notebook1" notebook1})
        cached-notes (list-cache-notes notebooks "notebook1")]
    (is (= ["note-title"] (vec cached-notes)))))