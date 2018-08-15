(ns tourme.debug-data
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            [tourme.migration.tour :as mt]))

(def test-str "\"You love having a second home but the mortgage is putting a crater in your wallet. Many second home owners turn to renting their property as a vacation rental to… defray the costs of ownership. How do you price a vacation home rental without overcharging but making enough to cover your costs? Do your research.\\n\\nFind out what other owners of, similar sized homes in the area are charging. You can ask a local real estate agent for a price range, scan local papers or go online. There are also vacation rental sites like eVaca.com. These types of sites have advertisements from owners around the world and weekly rates for the properties are listed.\\n\\nThe time of year you rent out a property is important as well. If you want to rent out a ski lodge in Vermont, August is not going to be your “high time” of year, but January will. If you are going to rent the property in an “off” time of year you will not be able to charge as much as if you were renting the property in a peak time.\\n\\nYou also want to figure out what lengths you want to rent your property for. A Florida property in July near the beach will go for top dollar for a week. However, that same property in January you might only attract the snowbirds who want to rent at a lower price and rent it out on a monthly basis. You have to answer questions like, “do I want to mess around with weekend or nightly rentals” and “is it worth the hassle”.\\n\\nFigure out how many weeks you need to rent your property in order to make a profit or at least pay the bills. Say you need to make $12000 a year in rental income. If, after looking at other rentals, a rate of maybe $1,000 a week seems reasonable than you know you need to rent the property out for at least 12 weeks a year. But you might have to rent for a few more weeks at a lower rate to make up for “off peak” times of the year or if you do not get someone to fill every vacant period.\\n\\nMost important, do not forget to spend time in your own vacation home. Owning the property and not being able to enjoy it defeats the purpose of having that second home!\"")
(def ids (map str (range)))

(def default-user
  {:js-obj nil,
   :id "lJZhQrUzGKOA9JMVUMy723ovK9J3",
   :anonymous? false,
   :email nil,
   :name nil})

(def anonymous-user
  {:js-obj nil,
   :id "lJZhQrUzGKOA9JMVUMy723ovK9J3",
   :anonymous? true,
   :email "test@test.de",
   :name "tester"})

(def default-position {:longitude 9.968869, :latitude 53.545146})
(def default-tour {:locations [{:picture       nil,
                                :description   ""
                                :position      {:latitude  53.57382742735845,
                                                :longitude 9.89224249497056},
                                :location-type :marker,
                                :name          ""}
                               {:picture       nil,
                                :description   ""
                                :position      {:latitude  53.578204618246744,
                                                :longitude 9.896704014390707},
                                :location-type :place,
                                :name          "Park"}
                               {:picture       nil,
                                :description   ""
                                :position      {:latitude  53.571471820990155,
                                                :longitude 9.898253660649061},
                                :location-type :marker,
                                :name          ""}
                               {:picture       nil,
                                :description   ""
                                :position      {:latitude  53.871471820990155,
                                                :longitude 9.898253660649061},
                                :location-type :marker,
                                :name          ""}
                               {:picture       nil,
                                :description   ""
                                :position      {:latitude  54,
                                                :longitude 9.896704014390707},
                                :location-type :viewpoint,
                                :name          "Skyscraper"}]
                   :rating {:score 3.3 :cnt 5}
                   :length 3300
                   :duration 2134
                   :creator (:id default-user)
                   :db-version 0
                   :name "tour 1"
                   :description "awesome tour made for best testers in the world. You rock guys."
                   :id (nth ids 0)})

(def default-tours
  {(nth ids 0) default-tour
   (nth ids 1) (assoc default-tour :name "Tour 2"
                                   :id (nth ids 1)
                                   :creator "different-user"
                                   :description "")
   (nth ids 2) (assoc default-tour :name "Tour 3"
                                   :id (nth ids 2))
   (nth ids 3) (assoc default-tour :name "Tour 4"
                                   :id (nth ids 3))
   (nth ids 4) (assoc default-tour :name "Tour 5"
                                   :id (nth ids 4)
                                   :creator "different-user")
   (nth ids 5) (assoc default-tour :name "Tour 6"
                                   :id (nth ids 5))
   (nth ids 6) (assoc default-tour :name "Tour 7"
                                   :id (nth ids 6))
   (nth ids 7) (assoc default-tour :name "Tour 8"
                                   :id (nth ids 7)
                                   :creator "different-user")
   (nth ids 8) (assoc default-tour :name "Tour 9"
                                   :id (nth ids 8))
   (nth ids 9) (assoc default-tour :name "Tour 10"
                                   :id (nth ids 9))})

(def default-db)

(def follow-tour-db
  (merge default-db {:followed-tour (nth ids 1)
                     :tours default-tours}))

(def edit-tour-db
  (merge default-db {:editable-tour (get default-tours (nth ids 1))
                     :tours default-tours}))

(def add-location-db
  edit-tour-db)

(def add-tour-db
  (merge default-db {:editable-tour mt/empty-tour}))

(def no-user-db
  default-db)

(defn distance-matrix-mock [{uri :uri on-success :on-success}]
  (let [no-of-elements (count (str/split
                                (some (fn [param] (when (str/includes? param "origins") param)) (str/split uri #"&"))
                                #"\|"))]
    (rf/dispatch (conj on-success
                       {:rows (mapv (fn [_] {:elements (mapv
                                                         (fn [_] {:distance {:value (+ 500 (rand-int 5000))}
                                                                  :duration {:value (+ 5 (rand-int 50))}})
                                                         (range no-of-elements))})
                                    (range no-of-elements))}))))

(defn mock-http []
  (rf/reg-fx
    :http-xhrio
    (fn [rq]
      (cond
        (str/includes? (:uri rq) "/api/distancematrix") (distance-matrix-mock rq)))))