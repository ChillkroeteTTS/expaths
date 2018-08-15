(ns tourme.subs
  (:require [re-frame.core :refer [reg-sub]]
            [tourme.style :as styl]
            [tourme.components :as c]))

;; -- Startup ----------------------------------------------------------------

(reg-sub :tick (fn [db _] (:tick db)))

(reg-sub
  :startup-ready?
  (fn [db _]
    (let [startup (get-in db [:async :startup])]
      (and (= (:imgs-loaded startup) styl/no-of-imgs)
           (= (:img-styles-loaded startup) styl/no-of-imgs)
           (:fb-setup startup)))))

(reg-sub
  :pos-status
  (fn [db _]
    (get-in db [:async :startup :position-status])))

(reg-sub
  :system-message
  (fn [db _]
    (:system-message db)))

(reg-sub
  :show-loading-screen?
  (fn [db _]
    (let [async (:async db)
          startup (:startup async)
          preds [[(:alter-tour? async) "Saving changes"]
                 [(= :progress (:calc-length (:http-status async))) "Calculating distance"]
                 [(:signing-in startup) "Checking credentials"]
                 [(:signing-up startup) "Creating account"]
                 [(:sending-reset-email async) "Sending reset email"]
                 [(:deleting-tour async) "Deleting tour"]
                 [(:accepting-policies startup) "Accepting Policies"]
                 [(:checking-consent startup) "Checking Privacy Policy"]
                 ]
          reasons (filter (comp identity first) preds)]
      {:reasons              (mapv second reasons)
       :show-loading-screen? (not (nil? (some identity (mapv first preds))))})))

(reg-sub
  :signed-in?
  (fn [db _]
    (some? (get-in db [:user :id]))))

(reg-sub
  :accepted-policies?
  (fn [db _]
    (:accepted-policies? db)))

;; ----------------------------

(reg-sub
  :fetching-tours?
  (fn [db _]
    (get-in db [:async :fetching-tours])))

(reg-sub
  :tour-loaded?
  (fn [db _]
    (some? (get-in db [:async :startup :tour-loaded]))))

(reg-sub
  :dirty-info-status?
  (fn [db _]
    (:dirty-info-status? db)))

(reg-sub ;;deprecated
  :tours
  (fn [db _]
    (vals (:tours db))))

(reg-sub
  :tours-map
  (fn [db _]
    (:tours db)))

(reg-sub
  :tour
  (fn [db [_ id]]
    (get-in db [:tours id])))

(reg-sub
  :position
  (fn [db _]
    (:gps-position db)))

(reg-sub
  :editable-tour
  (fn [db _]
    (:editable-tour db)))

(reg-sub
  :selected-location-index
  (fn [db _]
    (:selected-location-index db)))

(reg-sub
  :selected-tour-id
  (fn [db _]
    (:selected-tour-id db)))

(reg-sub
  :selected-location-position
  (fn [db _]
    (let [locations      (:locations (:editable-tour db))
          selected-index (:selected-location-index db)]
      (when (and selected-index (< selected-index (count locations)))
        (:position (nth locations selected-index))))))

(reg-sub
  :map-focus
  (fn [db _]
    (:map-focus db)))

(reg-sub
  :followed-tour
  (fn [db _]
    (let [id (:followed-tour db)]
      (get-in db [:tours id]))))

(reg-sub
  :directions
  (fn [db [_ id]]
    (get-in db [:directions id :directions])))

(reg-sub
  :user
  (fn [db _]
    (:user db)))