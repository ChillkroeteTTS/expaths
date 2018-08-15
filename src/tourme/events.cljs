(ns tourme.events
  (:require
    [re-frame.core :refer [reg-event-db after reg-event-fx reg-fx] :as rf]
    [clojure.spec.alpha :as s]
    [tourme.db :as db :refer [app-db]]
    [tourme.config :as config]
    [tourme.debug-data :as dd]
    [tourme.helper :as h]
    [clojure.string :as str]
    [tourme.logging :as l]
    [tourme.migration.tour :as mt]
    [cljs-time.core :as t]
    [reagent.core :as r]
    [polyline :as pl]))

;; -- Interceptors ------------------------------------------------------------
;;
;; See https://github.com/Day8/re-frame/blob/master/docs/Interceptors.md
;;
(defn check-and-throw
  "Throw an exception if db doesn't have a valid spec."
  [spec db [event]]
  (when-not (s/valid? spec db)
    (let [explain-data (s/explain-data spec db)]
      (throw (ex-info (str "Spec check after " event " failed: " explain-data) explain-data)))))

(def validate-spec
  (if goog.DEBUG
    (after (partial check-and-throw ::db/app-db))
    []))

;; -- Handlers --------------------------------------------------------------

(reg-event-db
  :initialize-db
  validate-spec
  (fn [_ _]
    (if goog.DEBUG
      (condp = config/debug-db-set
        :default (merge app-db dd/default-db)
        :follow-tour (merge app-db dd/follow-tour-db)
        :edit-tour (merge app-db dd/edit-tour-db)
        :add-tour (merge app-db dd/add-tour-db)
        :add-location (merge app-db dd/add-location-db)
        (do (prn "Warning! " config/debug-db-set " is no valid debug db set.") app-db))
      app-db))), ;; -- DEBUG  ---------------------------------------------------------------

(reg-event-db
  :update-position
  validate-spec
  (fn [db [_ position]]
    (assoc db :gps-position position)))

(reg-event-db
  :set-map-focus
  validate-spec
  (fn [db [_ position]]
    (assoc db :map-focus position)))

;; -- Startup ----------------------------------------------------------------

(reg-event-db
  :tick validate-spec
  (fn [db _] (assoc db :tick (t/now))))

(reg-event-db
  :img-loaded validate-spec
  (fn [db _] (update-in db [:async :startup :imgs-loaded] inc)))

(reg-event-db
  :img-style-loaded validate-spec
  (fn [db _] (update-in db [:async :startup :img-styles-loaded] inc)))

(reg-event-db
  :tour-loaded validate-spec
  (fn [db [_ time]] (assoc-in db [:async :startup :tour-loaded] time)))

;; -- System -----------------------------------------------------------------

(reg-event-fx
  :send-bug-report
  validate-spec
  (fn [_ [_ msg]]
    {:fs/save-bug-report msg}))

(reg-event-db
  :system-message
  validate-spec
  (fn [db [_ msg]]
    (when (some? msg)
      (js/setTimeout (fn [] (rf/dispatch [:system-message nil])) 4000))
    (assoc db :system-message msg)))

(reg-event-db
  :fb-setup
  validate-spec
  (fn [db [_ ]]
    (assoc-in db [:async :startup :fb-setup] true)))

(reg-event-fx
  :refresh-tours
  validate-spec
  (fn [{db :db} [_]]
    (if-let [refresh-info (:refresh-info db)]
      (let [fetch-center            (:position refresh-info)
            tour-fetch-radius-in-km (:radius-in-km refresh-info)]
        (l/dprn :rf-event/refresh-tours :fetching)
        {:db (-> (assoc-in db [:async :fetching-tours] true)
                 (assoc :selected-tour-id nil)
                 (update :refresh-info (fn [o] (assoc o :last-used refresh-info))))
         :fs/fetch-and-wire-tours
             {:fetch-center            fetch-center
              :tour-fetch-radius-in-km tour-fetch-radius-in-km
              :on-success              (fn []
                                         (rf/dispatch [:distance-tour-data])
                                         (rf/dispatch [:fetching-tours-status false])
                                         (rf/dispatch [:tour-loaded (t/now)]))
              :on-failure              (fn [] (rf/dispatch [:fetching-tours-status false]))}})
      (do
        (l/dprn :rf-event/refresh-tours :no-refresh-position)
        {:db db}))))

(reg-event-db
  :distance-tour-data
  validate-spec
  (fn [db [_]]
    (let [pos        (:gps-position db)
          tours      (:tours db)
          apply-tour (fn [f]
                       (reduce-kv
                         (fn [agg id tour] (assoc agg id (f tour)))
                         {} tours))]
      (if pos
        (do
          (l/dprn :rf-event/distance-tour-data :calculating)
          (assoc db :tours (apply-tour (partial h/add-distance pos))))
        (do
          (l/dprn :rf-event/distance-tour-data :missing-pos :set-to-nil)
          (assoc db :tours (apply-tour (fn [tour] (assoc tour :distance nil)))))))))

(reg-event-db
  :pos-status
  validate-spec
  (fn [db [_ status]] (assoc-in db [:async :startup :position-status] status)))

(reg-event-db
  :dirty-info-status?
  validate-spec
  (fn [db [_ status]] (assoc db :dirty-info-status? status)))

(defn dirty-info-status? [last-used-info new-info]
  (or (>= (- (:radius-in-km new-info) (:radius-in-km last-used-info)) ;; new radius is significantly bigger
          1)
      (>= (h/position-distance (:position last-used-info) (:position new-info))
          1500)))

(reg-event-fx
  :refresh-info
  validate-spec
  (fn [{db :db} [_ new-info-status]]
    (let [old-info-status (:refresh-info db)
          new-db          (assoc db :refresh-info (merge old-info-status new-info-status))]
      (cond
        (nil? old-info-status)
        {:db (assoc-in new-db [:refresh-info :last-used] nil) :dispatch [:refresh-tours]}
        (dirty-info-status? (:last-used old-info-status) new-info-status)
        {:db (assoc new-db :dirty-info-status? true)}
        :else
        {:db new-db}))))

(reg-event-db
  :register-watch
  validate-spec
  (fn [db [_ key watch-fn]]
    (when (nil? watch-fn) (l/dprn :rf-event/register-watch :empty-fn))
    (when-let [old-watch (get-in db [:watches key])]
      (l/dprn :rf-event/register-watch :clear key)
      (r/dispose! old-watch))
    (l/dprn :rf-event/register-watch :reg key)
    (assoc-in db [:watches key] (r/track! watch-fn))))

(reg-event-db
  :deregister-watch
  validate-spec
  (fn [db [_ key]]
    (if-let [watch (get-in db [:watches key])]
      (do
        (l/dprn :rf-event/deregister-watch :dereg key)
        (r/dispose! (get-in db [:watches key]))
        (update db :watches (fn [o] (dissoc o key))))
      (do
        (l/dprn :rf-event/deregister-watch :unknown key)
        db))))

(reg-event-db
  :fetching-tours-status
  validate-spec
  (fn [db [_ status]] (assoc-in db [:async :fetching-tours] status)))

;; -- Tour editing ------------------------------------------------------------

(reg-event-db
  :edit-new-tour
  validate-spec
  (fn [db _]
    (assoc db :editable-tour mt/empty-tour)))

(reg-event-db
  :edit-tour
  validate-spec
  (fn [db [_ id]]
    (assoc db :editable-tour (get-in db [:tours id]))))

(reg-event-db
  :tour-set-name
  validate-spec
  (fn [db [_ name]]
    (assoc db :editable-tour (assoc (:editable-tour db) :name name))))

(reg-event-db
  :tour-set-description
  validate-spec
  (fn [db [_ description]]
    (assoc db :editable-tour (assoc (:editable-tour db) :description description))))

(reg-event-db
  :tour-add-location
  validate-spec
  (fn [db [_ i {:keys [name description location-type position] :as loc}]]
    (l/dprn :rf-event/tour-add-location :add i loc)
    (let [updated-db (update-in db [:editable-tour :locations]
                                (fn [loc-list]
                                  (let [[front tail] (split-at i loc-list)]
                                    (h/concatv
                                      front
                                      [{:name          name
                                        :description   description
                                        :location-type location-type
                                        :picture       nil
                                        :position      position}]
                                      tail))))]
      (if-let [sel-loc-i (:selected-location-index updated-db)]
        (assoc updated-db :selected-location-index (+ 1 sel-loc-i))
        (assoc updated-db :selected-location-index 0)))))
(defn update-editable-locations [db update-fn]
  (let [locations (get-in db [:editable-tour :locations])]
    (assoc-in db [:editable-tour :locations] (update-fn locations))))
(rf/reg-event-fx
  :tour-remove-selected-location
  validate-spec
  (fn [{db :db} [_]]
    (let [index             (:selected-location-index db)
          db-w-updated-loc  (update-editable-locations
                              db
                              (fn [locations] (mapv second (remove (fn [[i _]] (= i index))
                                                                   (map-indexed vector locations)))))
          locations-in-tour (count (get-in db-w-updated-loc [:editable-tour :locations]))
          lowest-index      (min index (- locations-in-tour 1))
          valid-index       (if (< lowest-index 0) nil lowest-index)]
      (l/dprn :rf-event/tour-remove-selected-location (str
                                                        "Removed " index ", " locations-in-tour " locations left. "
                                                        "New valid index" " is " valid-index))
      {:db       db-w-updated-loc
       :dispatch [:tour-select-location valid-index]})))

(reg-event-db
  :tour-assoc-location
  validate-spec
  (fn [db [_ index location]]
    (l/dprn :rf-event/tour-assoc-location (str "Assoc location " location " to index " index))
    (update-editable-locations db (fn [locations] (assoc locations index location)))))

(reg-event-fx
  :tour-select-location
  validate-spec
  (fn [{db :db} [_ index]]
    (merge {:db (assoc db :selected-location-index index)}
           (if index
             {:dispatch [:set-map-focus (:position (nth (get-in db [:editable-tour :locations]) index))]}
             {}))))

(reg-event-db
  :selected-tour-id
  (fn [db [_ id]]
    (assoc db :selected-tour-id id)))

(reg-event-db
  :assoc-tour
  validate-spec
  (fn [db [_ {id :id :as tour}]]
    (->
      (assoc-in db [:tours id] tour))))

(reg-event-db
  :assoc-tours
  validate-spec
  (fn [db [_ tours]]
    (->
      (assoc db :tours tours))))

(reg-event-fx
  :tour-saved
  validate-spec
  (fn [{db :db} [_ succeeded?]]
    {:db       (assoc-in db [:async :alter-tour?] false)
     :dispatch [:refresh-tours]}))

(reg-event-fx
  :save-current-tour
  validate-spec
  (fn [{db :db} [_ on-success on-failure]]
    {:db       db
     :dispatch [:calc-length (:editable-tour db) on-success on-failure]}))

(reg-event-fx
  :save-tour
  validate-spec
  (fn [{db :db} [_ {id :id :as tour} on-success on-failure]]
    (let [user-id          (get-in db [:user :id])
          user-signed-tour (assoc tour :creator user-id)]
      (when (nil? user-id)
        (l/dprn :rf-event/save-tour :ERROR :empty-user-id)
        (throw (ex-info "Can't save tour due to missing user-id" (:user db))))
      (when (not (s/valid? ::db/tour user-signed-tour))
        (let [explain-data (s/explain-data ::db/user-signed-tour user-signed-tour)]
          (throw (ex-info (str "Trying to save " user-signed-tour " failed: " explain-data) explain-data))))
      (if (= "new" id)
        {:db          (assoc-in db [:async :alter-tour?] true)
         :fs/add-tour {:tour       user-signed-tour
                       :on-success (fn [id]
                                     (rf/dispatch [:tour-saved true])
                                     (on-success id))
                       :on-failure (fn [e]
                                     (rf/dispatch [:tour-saved false])
                                     (on-failure e))}}
        {:db            (assoc-in db [:async :alter-tour?] true)
         :fs/alter-tour {:tour       user-signed-tour
                         :on-success (fn []
                                       (rf/dispatch [:tour-saved true])
                                       (on-success))
                         :on-failure (fn [e]
                                       (rf/dispatch [:tour-saved false])
                                       (on-failure e))}}))))

(reg-event-db
  :follow-tour
  validate-spec
  (fn [db [_ id]]
    (assoc db :followed-tour id)))

(reg-event-fx
  :rate-tour
  validate-spec
  (fn [{db :db} [_ tour {n-score :score :as rating} on-success]]
    (if (> n-score 0)
      (let [{o-score :score o-cnt :cnt} (:rating tour)
            n-cnt (inc o-cnt)
            avg   (/ (+ (* o-score o-cnt) n-score) n-cnt)]
        (l/dprn :rate-tour "Old rating: " o-score ". New rating " avg)
        {:db db :dispatch [:save-tour
                           (assoc tour :rating {:score avg :cnt n-cnt})
                           on-success]})
      (do
        (l/dprn :rate-tour "User did not supply any rating")
        {:db db}))))

;; (hash) uses (floor) on decimals
(defn loc-hash [tour] (mapv hash (map (partial * 100000000000) (flatten (map vals (map :position (:locations tour)))))))
(reg-event-db
  :assoc-directions
  validate-spec
  (fn [db [_ tour directions on-success]]
    (l/dprn :rf-event/assoc-directions (:id tour) (count directions))
    (js/setTimeout on-success 100) ;; some time to update app-db, otherwise 0 waypoints
    (assoc-in db [:directions (:id tour)]
              {:directions      directions
               :locations-count (count (:locations tour))
               :location-hashes (loc-hash tour)})))



;; ----------------------------- HTTP -------------------------------

(defn summed-length+duration [result]
  "first origin will be compared with first destination, second with second etc."
  (let [elements (map :elements (:rows result))
        results  (map-indexed (fn [i element] (nth element i)) elements)]
    {:length   (reduce + (map (comp :value :distance) results))
     :duration (reduce + (map (comp :value :duration) results))}))

(defn polyline-positions [json]
  (js->clj (pl/decode (get-in (first (:routes json)) [:overview_polyline :points]))))

(reg-event-fx
  :http-success
  validate-spec
  (fn [{db :db} [_ k tour on-success on-failure result]]
    (let [ndb (-> (update-in db [:async :open-http-requests] dec)
                  (update-in [:async :http-status k] :success))]
      (l/dprn :rf-event/http-success k "Received: " result)
      (let [api-status (:status result)]
        (if (not= "OK" api-status)
          (do
            (l/dprn :rf-event/http-success k "WARNING! Google API error: " api-status ".")
            {:db ndb})
          (condp = k
            :calc-length
            {:db       ndb
             :dispatch [:save-tour (merge tour (summed-length+duration result)) on-success on-failure]}

            :request-directions
            {:db       ndb
             :dispatch [:assoc-directions tour (polyline-positions result) on-success]}))))))

(reg-event-db
  :http-failure
  validate-spec
  (fn [db [_ k on-failure result]]
    (l/dprn :rf-event/http-failure k "received: " result)
    (on-failure)
    (-> (update-in db [:async :open-http-requests] dec)
        (update-in [:async :http-status k] :failure))))

(reg-event-fx
  :calc-length
  validate-spec
  (fn [{:keys [db]} [_ tour on-success on-failure]]
    (if (>= (count (:locations tour)) 2)
      (let [pos-tuple        (partition 2 1 (map :position (:locations tour)))
            pos->str         (fn [pos] (str (:latitude pos) "," (:longitude pos)))
            build-str        (fn [f] (str/join "|" (map pos->str (map f pos-tuple))))
            origins-str      (build-str first)
            destinations-str (build-str second)]
        {:http-xhrio {:method     :get
                      :uri        (str "https://maps.googleapis.com/maps/api/distancematrix/json?units=metric&mode=walking"
                                       "&origins=" origins-str
                                       "&destinations=" destinations-str
                                       "&key=AIzaSyBKWgGuraNlQu39iOUGMBwlwswvrEOCNuc")
                      :on-success [:http-success :calc-length tour on-success on-failure]
                      :on-failure [:http-failure :calc-length on-failure]}
         :db         (-> (update-in db [:async :open-http-requests] inc)
                         (assoc-in [:async :http-status :calc-length] :progress))})
      (do
        (on-failure)
        {:db db}))))

(reg-event-fx
  :request-directions
  validate-spec
  (fn [{:keys [db]} [_ tour on-success on-failure]]
    (let [id     (:id tour)
          cached (get-in db [:directions id])]
      (cond
        (and cached
             (= (count (:locations tour)) (:locations-count cached))
             (every? identity (map = (loc-hash tour) (:location-hashes cached))))
        (do
          (l/dprn :rf-event/request-directions :cached id)
          (on-success)
          {:db db})
        (>= (count (:locations tour)) 2)
        (let [locations   (:locations tour)
              origin      (first locations)
              destination (last locations)
              waypoints   (drop-last (rest locations))]
          (l/dprn :rf-event/request-directions :start-request (:id tour))
          {:http-xhrio {:method     :get
                        :uri        (str "https://maps.googleapis.com/maps/api/directions/json?units=metric&mode=walking"
                                         "&origin=" (h/location->str origin)
                                         "&destination=" (h/location->str destination)
                                         "&waypoints=" (str/join "|" (map h/location->str waypoints))
                                         "&key=AIzaSyBKWgGuraNlQu39iOUGMBwlwswvrEOCNuc")
                        :on-success [:http-success :request-directions tour on-success on-failure]
                        :on-failure [:http-failure :request-directions on-failure]}
           :db         (-> (update-in db [:async :open-http-requests] inc)
                           (update-in [:async :http-status :request-directions] :progress))})
        (nil? id)
        (l/dprn :rf-event/request-directions :no-tour-id-provided)
        :else
        (do
          (l/dprn :rf-event/request-directions :not-enough-locations id)
          (on-failure)
          {:db db})))))

(reg-event-db
  :deleting-tour
  validate-spec
  (fn [db [_ status]]
    (assoc-in db [:async :deleting-tour] status)))
(reg-event-fx
  :delete-tour
  validate-spec
  (fn [{db :db} [_ id on-success on-failure]]
    (l/dprn :rf-event/delete-tour "Trying to delete tour " id)
    (assoc {:fb/delete-tour {:id         id
                             :on-success (fn []
                                           (rf/dispatch [:deleting-tour false])
                                           (on-success))
                             :on-failure (fn [e]
                                           (rf/dispatch [:deleting-tour false])
                                           (on-failure e))}}
      :dispatch [:deleting-tour true]
      :db db)))

;; ------------------------------- USER MANGAGEMENT --------------------------------------
(reg-event-db
  :found-consent
  validate-spec
  (fn [db [_ status]]
    (assoc db :accepted-policies? status)))

(reg-event-db
  :checking-consent
  validate-spec
  (fn [db [_ status]]
    (assoc-in db [:async :startup :checking-consent] status)))

(reg-event-fx
  :check-policy-consent
  validate-spec
  (fn [{db :db} _]
    (l/dprn :rf-event/check-policy-consent (get-in db [:user :id]))
    {:db                   db
     :fb/accepted-policies {:user-id    (get-in db [:user :id])
                            :on-success (fn [consent]
                                          (l/dprn :rf-event/check-policy-consent :true)
                                          (rf/dispatch [:checking-consent false])
                                          ;; nav to consent screen is automatic due to found-consent state
                                          (rf/dispatch [:found-consent consent]))
                            :on-failure (fn [e]
                                          (l/dprn :rf-event/check-policy-consent :false)
                                          (rf/dispatch [:checking-consent false])
                                          (js/alert "There was a problem connecting to the server. Please try again later."))}
     :dispatch             [:checking-consent true]}))

(reg-event-db
  :accepting-policies
  validate-spec
  (fn [db [_ status]]
    (assoc-in db [:async :startup :accepting-policies] status)))

(reg-event-fx
  :accept-policies
  validate-spec
  (fn [{db :db} [_ on-success on-failure]]
    (l/dprn :rf-event/accept-policies)
    {:fb/accept-policies {:user-id    (get-in db [:user :id])
                          :on-success (fn []
                                        (rf/dispatch [:accepting-policies false])
                                        (rf/dispatch [:found-consent true])
                                        (on-success))
                          :on-failure (fn [e]
                                        (rf/dispatch [:accepting-policies false])
                                        (on-failure e))}
     :dispatch           [:accepting-policies true]
     :db                 db}))

(reg-event-db
  :signing-in
  validate-spec
  (fn [db [_ status]]
    (assoc-in db [:async :startup :signing-in] status)))

(reg-event-db
  :signing-up
  validate-spec
  (fn [db [_ status]]
    (assoc-in db [:async :startup :signing-up] status)))

(reg-event-fx
  :sign-in
  validate-spec
  (fn [{db :db} [_ email pw on-success on-failure]]
    (l/dprn :rf-event/sign-in "Trying to sign in " email " with password length" (when pw
                                                                                   (count pw)))
    (assoc {:fb/sign-in {:email      email
                         :password   pw
                         :on-success (fn []
                                       (rf/dispatch [:signing-in false])
                                       (on-success))
                         :on-failure (fn [e]
                                       (rf/dispatch [:signing-in false])
                                       (on-failure e))}}
      :dispatch [:signing-in true]
      :db db)))

(reg-event-fx
  :sign-in-social
  validate-spec
  (fn [{db :db} [_ provider on-success on-failure]]
    (l/dprn :rf-event/sign-in-social "Trying to sign in with " provider)
    (assoc {:fb/sign-in-social {:provider   provider
                                :on-success (fn []
                                              (rf/dispatch [:signing-in false])
                                              (on-success))
                                :on-failure (fn [e]
                                              (rf/dispatch [:signing-in false])
                                              (on-failure e))}}
      :dispatch [:signing-in true]
      :db db)))

(reg-event-fx
  :sign-up
  validate-spec
  (fn [{db :db} [_ email pw on-success on-failure]]
    (l/dprn :rf-event/sign-up "Trying to sign up " email " with password length" (when pw
                                                                                   (count pw)))
    (assoc {:fb/sign-up {:email      email
                         :password   pw
                         :on-success (fn []
                                       (rf/dispatch [:signing-up false])
                                       (rf/dispatch [:accept-policies
                                                     on-success
                                                     on-failure]))
                         :on-failure (fn [e]
                                       (rf/dispatch [:signing-up false])
                                       (on-failure e))}}
      :dispatch [:signing-up true]
      :db db)))

(reg-event-db
  :sending-reset-email
  validate-spec
  (fn [db [_ status]]
    (assoc-in db [:async :sending-reset-email] status)))

(reg-event-fx
  :reset-password
  validate-spec
  (fn [{db :db} [_ email on-success on-failure]]
    (l/dprn :rf-event/reset-password "Trying to reset password for " email)
    (assoc {:fb/reset-password {:email      email
                                :on-success (fn []
                                              (rf/dispatch [:sending-reset-email false])
                                              (on-success))
                                :on-failure (fn [e]
                                              (rf/dispatch [:sending-reset-email false])
                                              (on-failure e))}}
      :dispatch [:sending-reset-email true]
      :db db)))

(reg-event-fx
  :sign-out
  validate-spec
  (fn [{db :db} [_]]
    (l/dprn :rf-event/sign-out)
    {:fb/sign-out nil
     :db          (assoc db
                    :user nil
                    :accepted-policies? nil)}))

(reg-event-db
  :set-user
  validate-spec
  (fn [db [_ user]]
    (assoc db :user user)))

(reg-event-fx
  :sign-in-anonymously
  validate-spec
  (fn [{db :db} [_ on-success on-failure]]
    (l/dprn :rf-event/:sign-in-anonymously)
    {:db                     db
     :dispatch               [:signing-in true]
     :fb/sign-in-anonymously {:on-success (fn []
                                            (l/dprn :rf-event/:sign-in-anonymously :success)
                                            (rf/dispatch [:signing-in false])
                                            (on-success))
                              :on-failure (fn [e]
                                            (l/dprn :rf-event/:sign-in-anonymously :failure)
                                            (l/dprn :rf-event/:sign-in-anonymously e)
                                            (rf/dispatch [:signing-in false])
                                            (on-failure e))}}))


