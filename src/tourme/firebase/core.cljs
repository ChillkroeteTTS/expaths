(ns tourme.firebase.core
  (:require [tourme.config :as config]
            [cljs.core.async :refer [put! chan <! >! timeout close! go go-loop]]
            [re-frame.core :as rf]
            [tourme.logging :as l]
            [tourme.migration.tour :as mt]
            [tourme.firebase.firebase :as impl]
            [tourme.firebase.firebase-mock :as mock]
            [tourme.firebase.firebase-prtcl :as prtcl]
            [tourme.firebase.helper :as h]))

(def firebase (atom nil))

(defn setup! []
  (reset! firebase (if config/mock-firebase? (mock/->FirebaseMock) (impl/->Firebase)))
  (prtcl/setup! @firebase)
  (prtcl/sign-in-known-user! @firebase)
  (when-let [user @(rf/subscribe [:user])]
    (prtcl/accepted-policies! @firebase
                              (:id user)
                              (fn [consent] (rf/dispatch-sync [:found-consent consent]))
                              (fn [] "There was a problem communicating with the server. Please try again later.")))
  (rf/dispatch [:fb-setup]))

(defn app-state->serializable [state]
  (-> (dissoc state :tick :user)
      (update-in [:async :startup :tour-loaded] #(.toString %))
      (update :watches (fn [watches]
                         (reduce-kv
                           (fn [agg k v] (assoc agg k :set))
                           {} watches)))))

(rf/reg-fx
  :fs/alter-tour
  (fn [{:keys [tour on-success on-failure]}]
    (prtcl/merge-document!
      @firebase
      (:id tour)
      tour
      on-success
      on-failure)))

(rf/reg-fx
  :fs/fetch-and-wire-tours
  (fn [{:keys [on-success on-failure fetch-center tour-fetch-radius-in-km]}]
    (h/dprn "Fetching new tours")
    (prtcl/fetch-and-wire-tours! @firebase fetch-center tour-fetch-radius-in-km on-success on-failure)))

(rf/reg-fx
  :fs/add-tour
  (fn [{:keys [tour on-success on-failure]}]
    (prtcl/add-tour!
      @firebase
      tour
      on-success
      on-failure)))

(rf/reg-fx
  :fs/save-bug-report
  (fn [msg]
    (prtcl/save-bug-report!
      @firebase
      (app-state->serializable @re-frame.db/app-db)
      (l/logs-snapshot)
      msg)))

(rf/reg-fx
  :fb/accepted-policies
  (fn [{:keys [user-id on-success on-failure]}]
    (prtcl/accepted-policies! @firebase user-id on-success on-failure)))

(rf/reg-fx
  :fb/accept-policies
  (fn [{:keys [user-id on-success on-failure]}]
    (prtcl/accept-policies! @firebase user-id on-success on-failure)))

(rf/reg-fx
  :fb/sign-in
  (fn [{:keys [email password on-success on-failure]}]
    (prtcl/sign-in! @firebase email password on-success on-failure)))

(rf/reg-fx
  :fb/sign-in-social
  (fn [{:keys [provider on-success on-failure]}]
    (prtcl/sign-in-social! @firebase provider on-success on-failure)))

(rf/reg-fx
  :fb/sign-in-anonymously
  (fn [{:keys [on-success on-failure]}]
    (prtcl/sign-in-anonymously! @firebase on-success on-failure)))

(rf/reg-fx
  :fb/sign-up
  (fn [{:keys [email password on-success on-failure]}]
    (prtcl/sign-up! @firebase email password on-success on-failure)))

(rf/reg-fx
  :fb/sign-out
  (fn []
    (prtcl/sign-out! @firebase)))

(rf/reg-fx
  :fb/reset-password
  (fn [{:keys [email on-success on-failure]}]
    (prtcl/reset-password! @firebase email on-success on-failure)))

(rf/reg-fx
  :fb/delete-tour
  (fn [{:keys [id on-success on-failure]}]
    (prtcl/delete-tour! @firebase id on-success on-failure)))