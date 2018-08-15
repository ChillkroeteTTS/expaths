(ns tourme.db
  (:require [clojure.spec.alpha :as s]
            [cljs-time.core :as t]
            [tourme.config :as config]))

(s/def ::nilable-int (s/nilable int?))

(defn max-chars [limit str] (<= (count str) limit))

;; spec of app-db
(s/def ::id (s/nilable string?))
(s/def ::location-type #{:marker :place :food :cafe :viewpoint})
(s/def ::name (partial max-chars config/max-chars-name))
(s/def ::description (partial max-chars config/max-chars-loc-description))
(s/def ::location (s/keys :req-un [::name
                                   ::description
                                   ::location-type
                                   ::picture
                                   ::position]))
(s/def ::locations (s/and vector? (s/coll-of ::location)))
(s/def ::score (fn [v] (and (<= 0 v 5) (double? v))))
(s/def ::cnt int?)
(s/def ::rating (s/keys :req-un [::cnt ::score]))
(s/def ::length ::nilable-int)
(s/def ::duration ::nilable-int)
(s/def ::tour (s/keys :req-un [::id
                               ::name
                               ::description
                               ::creator
                               ::length
                               ::duration
                               ::locations
                               ::rating
                               ::db-version]))
(s/def ::tours (s/nilable
                 (s/map-of ::id ::tour)))
(s/def ::followed-tours (s/nilable uuid?))

(s/def ::longitude number?)
(s/def ::latitude number?)
(s/def ::position (s/keys :req-un [::longitude ::latitude]))
(s/def ::gps-position (s/nilable ::position))
(s/def ::map-focus (s/nilable ::position))

(s/def ::editable-tour (s/nilable ::tour))
(s/def ::directions (s/nilable
                      (s/map-of ::id (s/coll-of vector?))))
(s/def ::user (s/nilable (s/keys :req-un [::js-obj
                                          ::id
                                          ::name
                                          ::email
                                          ::anonymous?])))
(s/def ::watches map?)
(s/def ::system-message (s/nilable string?))

(s/def ::last-used (s/nilable (s/keys :req-un [::position ::radius-in-km])))
(s/def ::refresh-info (s/nilable (s/keys :req-un [::position ::radius-in-km ::last-used])))

(s/def ::accepted-policies? (s/nilable boolean?))

(s/def ::app-db
  (s/keys :req-un [::async
                   ::comp-control
                   ::gps-position
                   ::map-focus
                   ::accepted-policies?
                   ::editable-tour
                   ::refresh-info
                   ::tours
                   ::followed-tour
                   ::directions
                   ::user
                   ::tick]))

;; initial state of app-db
(def app-db {:async                   {:open-http-requests  0
                                       :http-status         {:calc-length        :not-done
                                                             :request-directions :not-done}
                                       :alter-tour?         false
                                       :fetching-tours      false
                                       :sending-reset-email false
                                       :deleting-tour       false
                                       :startup             {:imgs-loaded       0
                                                             :img-styles-loaded 0
                                                             :fb-setup false
                                                             :position-status   :unknown
                                                             :signing-in        false
                                                             :signing-up        false
                                                             :tour-loaded       nil
                                                             :accepting-policies false
                                                             :checking-consent false}}
             :system-message          nil
             :accepted-policies?      nil
             :tick                    (t/now)
             :watches                 {}
             :comp-control            nil
             :gps-position            nil
             :refresh-info            nil
             :dirty-info-status?      false
             :map-focus               nil
             :selected-location-index nil
             :selected-tour-id        nil
             :editable-tour           nil
             :followed-tour           nil
             :tours                   nil
             :directions              {}
             :user                    nil})