(ns tourme.migration.tour
  (:require [clojure.string :as str]))

(def empty-tour {:id         "new"
                 :name       ""
                 :description ""
                 :rating     {:cnt 0 :score 0}
                 :duration   nil
                 :length     nil
                 :locations  []
                 :creator    nil
                 :db-version 5})

(defn t-0->1 [tour] (assoc tour :db-version 1))
(defn t-1->2 [tour]
  (let [old-r (:rating tour)]
    (assoc tour :db-version 2
                :rating {:cnt (if (= 0 old-r) 0 1) :score (double old-r)})))
(defn t-2->3 [tour]
  (assoc tour :db-version 3
              :creator nil))

(defn trim-name [name] (if (> (count name) 30) (str/join (take 30 name)) name))
(defn t-3->4 [tour]
  (-> (assoc tour :db-version 4
                  :locations (mapv
                               (fn [location]
                                 (->
                                   (assoc location :description "")
                                   (update :name trim-name)))
                               (:locations tour)))
      (update :name trim-name)))

(defn t-4->5 [tour] (assoc tour :db-version 5 :description ""))

(defn check-and-migrate-tour [tour]
  (let [migrate-fns [t-0->1 t-1->2 t-2->3 t-3->4 t-4->5]
        tour-v      (or (:db-version tour) 0)]
    ((apply comp (reverse (drop tour-v migrate-fns))) tour)))