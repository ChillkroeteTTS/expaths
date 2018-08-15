(ns tourme.helper
  (:require [cljs.core :refer [for]]
            [cljs.core.async :refer [<! chan]]
            [tourme.logging :as l]
            [cljs-time.core :as t]
            [goog.string :as gstring]
            [goog.string.format]
            [cljs-time.format :as tf])
  (:require-macros [cljs.core.async :refer [go-loop]]))

(defn concatv [& args]
  (into [] (apply concat args)))

(defn to-rad [d] (/ (* Math/PI d) 180))

(defn position-distance [{lon1 :longitude lat1 :latitude} {lon2 :longitude lat2 :latitude}]
  (let [lat1r  (to-rad lat1)
        lat2r  (to-rad lat2)
        d-latr (to-rad (- lat2 lat1))
        d-lonr (to-rad (- lon2 lon1))
        a      (+ (* (Math/sin (/ d-latr 2)) (Math/sin (/ d-latr 2)))
                  (* (Math/cos lat1r) (Math/cos lat2r)
                     (Math/sin (/ d-lonr 2)) (Math/sin (/ d-lonr 2))))
        c      (* 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))]
    (* 6371e3 c)))

(defn add-distance [position tour]
  (if (and position (first (:locations tour)))
    (let [dist-start (position-distance (:position (first (:locations tour))) position)]
      (assoc tour :distance dist-start))
    (assoc tour :distance nil)))

(defn ra! [a v] (reset! a v))

(defn duration->str [duration]
  (if duration
    (str (tf/unparse (:hour-minute tf/formatters)
                     (t/plus (t/epoch) (t/seconds duration)))
         " h")
    "- -"))

(defn distance->str [distance]
  (if distance
    (gstring/format "%.1f km" (/ distance 1000))
    "- -"))

(defn location->str [location]
  (let [pos (:position location)]
    (str (:latitude pos) "," (:longitude pos))))