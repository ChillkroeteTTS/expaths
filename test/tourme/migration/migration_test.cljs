(ns tourme.migration.migration-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [cljs.spec.alpha :as s]
            [tourme.db :as db]
            [tourme.migration.tour :as m]))

(def v0 {:name      "Dummy tour"
         :id        "1"
         :rating    0
         :length    nil
         :duration  nil
         :locations [{:name          "Dummy location"
                      :location-type :marker
                      :picture       nil
                      :position      {:longitude 9.988440, :latitude 53.543739}}
                     {:name          "Dummy location"
                      :location-type :place
                      :picture       nil
                      :position      {:longitude 9.988440, :latitude 53.543739}}
                     {:name          "Dummy location"
                      :location-type :place
                      :picture       nil
                      :position      {:longitude 9.999990, :latitude 53.543739}}]})

(deftest spec-test
  (testing "Empty tour conforms to spec"
    (is (nil? (s/explain-data ::db/tour m/empty-tour))))
  (testing "migrated tour conforms to spec"
    (is (nil? (s/explain-data ::db/tour (m/check-and-migrate-tour v0)))))
  (testing "migrated tour has version number is the same as empty tour"
    (is (= (:db-version (m/check-and-migrate-tour v0))
           (:db-version m/empty-tour)))))