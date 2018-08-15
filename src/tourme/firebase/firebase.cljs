(ns tourme.firebase.firebase
  (:require [tourme.migration.tour :as mt]
            [re-frame.core :as rf]
            [tourme.firebase.helper :as h]
            [tourme.firebase.social :as social]
            [tourme.firebase.firebase-prtcl :as prtcl]
            [cljs.core.async :refer [>! <! chan put!]]
            [cljs-time.core :as t]
            [positionHelper :as ph])
  (:require-macros [cljs.core.async :refer [go go-loop]]))

(def fb (.-default (js/require "react-native-firebase")))
(def fs (.firestore fb))
(def fb-auth (.auth fb))
(def GeoPoint (.. fb -firestore -GeoPoint))

(def local-doc-refs (atom {}))
(def tours-collection (.collection fs "tours"))
(def bug-reports-collection (.collection fs "bug-reports"))
(def consent-collection (.collection fs "consents"))
(def subscriptions (atom {}))

(defn unsubscribe-all! []
  (run! (fn [[_ f]] (f)) @subscriptions)
  (reset! subscriptions {}))

(defn remove-doc [id]
  ((get @subscriptions id))
  (swap! subscriptions (fn [o] (dissoc o id)))
  (swap! local-doc-refs (fn [o] (dissoc o id))))

(defn fb-pos->pos [fb-pos] {:latitude (.-latitude fb-pos) :longitude (.-longitude fb-pos)})

(defn err->clj [res]
  (when-let [code (.-code res)]
    (condp = code
      "auth/user-not-found" :invalid-credentials
      "auth/email-already-in-use" :email-already-in-use
      "auth/invalid-email" :invalid-email
      "auth/user-disabled" :disabled-user
      "auth/wrong-password" :invalid-credentials
      (h/dprn "Warning! Invalid error code " code "!"))))

(defn doc-snap->tour [fb-tour]
  (let [id   (.-id fb-tour)
        data (.data fb-tour)]
    (-> (js->clj data :keywordize-keys true)
        (update :start-position fb-pos->pos)
        (update :locations (fn [locs]
                             (mapv (fn [loc] (-> loc
                                                 (update :position fb-pos->pos)
                                                 (update :location-type keyword))) locs)))
        (assoc :id id)
        (dissoc :start-position))))

(defn tour->doc [tour]
  (-> (dissoc tour :id)
      (update :locations
              (fn [locs] (mapv
                           (fn [loc] (update loc :position
                                             (fn [pos] (GeoPoint.
                                                         (:latitude pos)
                                                         (:longitude pos)))))
                           locs)))
      ((fn [tour] (assoc tour :start-position (:position (first (:locations tour))))))
      (clj->js)))

(defn fb-user->cljs [fb-user]
  {:js-obj     fb-user
   :id         (.-uid fb-user)
   :anonymous? (.-isAnonymous fb-user)
   :email      (.-email fb-user)
   :name       (.-displayName fb-user)})


(defn wait-for-success! [ch no-of-docs on-success timeout-in-millis]
  (let [start (t/now)
        tours (atom [])]
    (go-loop
      [no-of-retured-docs 0]
      (cond
        (>= no-of-retured-docs no-of-docs)
        (do
          (rf/dispatch-sync [:assoc-tours (into {}
                                                (mapv (fn [{id :id :as tour}] [id tour])
                                                      @tours))])
          (h/dprn "Successfully wired up " no-of-retured-docs " documents to rf-state!")
          (on-success))
        (>= (t/in-millis (t/interval start (t/now))) timeout-in-millis)
        (h/dprn "ERROR! Wiring up " no-of-docs " timed out. " no-of-retured-docs " documents wired so far")
        :else (let [doc-snap (<! ch)]
                (swap! tours #(conj % doc-snap))
                (recur (+ no-of-retured-docs 1)))))))

(defn wire-documents-to-state! [on-success on-failure]
  (let [doc-refs   @local-doc-refs
        no-of-docs (count doc-refs)
        ch         (chan)]
    (h/dprn "Removing " (count @subscriptions) " subscriptions...")
    (unsubscribe-all!)
    (h/dprn "(Re)Wiring up " no-of-docs " tours to reframe...")
    (run!
      (fn [[id doc-ref]]
        (swap! subscriptions
               (fn [o] (assoc o id
                                (.onSnapshot
                                  doc-ref
                                  (fn [doc-snap]
                                    (put! ch (mt/check-and-migrate-tour
                                               (doc-snap->tour doc-snap)))))))))
      doc-refs)
    (wait-for-success! ch no-of-docs on-success 5000)))

(defrecord Firebase []
  prtcl/Firebase

  (setup! [this])

  (merge-document! [_ id tour on-success on-failure]
    (h/dprn :merge id tour)
    (let [doc-ref  (.doc (.collection fs "tours") id)
          tour-doc (tour->doc tour)]
      (.then (.runTransaction fs
                              (fn [transaction]
                                (-> (.get transaction doc-ref)
                                    (.then
                                      (fn [doc]
                                        (if (.-exists doc)
                                          (.update transaction doc-ref
                                                   tour-doc)
                                          (h/dprn (str id " does not exist."))))))))
             on-success
             (fn [e] (h/dprn e) (on-failure e)))))
  ;; https://stackoverflow.com/a/48411838/5433338
  ;; .where('location', '>', lesserGeopoint).where('location', '<', greaterGeopoint)
  (fetch-and-wire-tours! [_ fetch-center tour-fetch-radius-in-km on-success on-failure]
    (let [box              (ph/boundingBoxCoordinates (clj->js fetch-center) tour-fetch-radius-in-km)
          lesser-geopoint  (GeoPoint. (.. box -swCorner -latitude) (.. box -swCorner -longitude))
          greater-geopoint (GeoPoint. (.. box -neCorner -latitude) (.. box -neCorner -longitude))]
      (reset! local-doc-refs {})
      (unsubscribe-all!)
      (.then (.get (->
                     tours-collection
                     (.where "start-position" ">" lesser-geopoint)
                     (.where "start-position" "<" greater-geopoint)))
             (fn [query-snap]
               (h/dprn "Found tours in db...")
               (.forEach query-snap
                         (fn [doc-snap]
                           (swap! local-doc-refs
                                  (fn [o] (assoc o (.-id doc-snap)
                                                   (.doc tours-collection
                                                         (.-id doc-snap)))))))
               (wire-documents-to-state! on-success on-failure))
             (fn [e]
               (h/dprn e)))))

  (add-tour! [_ tour on-success on-failure]
    (let [doc (tour->doc tour)]
      (.then (.add tours-collection doc)
             (fn [doc-ref]
               (h/dprn "Created document with id " (.-id doc-ref))
               (swap! local-doc-refs (fn [o] (assoc o (.-id doc-ref) doc-ref)))
               (wire-documents-to-state! identity identity) ;; TODO only rewire the new tour
               (on-success doc-ref))
             on-failure)))

  (delete-tour! [this id on-success on-failure]
    (h/dprn :delete id)
    (let [doc-ref (.doc (.collection fs "tours") id)]
      (remove-doc id)
      (.then (.delete doc-ref)
             on-success
             (fn [e] (h/dprn e) (on-failure e)))))

  (save-bug-report! [this state logs msg]
    (.add bug-reports-collection
          (clj->js
            {:state state :logs logs :msg msg})))

  (reset-password! [_ email on-success on-failure]
    (.then (.sendPasswordResetEmail fb-auth email)
           (fn [_] (on-success))
           (fn [err]
             (let [cljs-code (err->clj err)]
               (h/dprn "Sign up failed with code " cljs-code)
               (on-failure cljs-code)))))

  (accepted-policies! [this user-id on-success on-failure]
    (h/dprn "Search for consents")
    (.then (.get (->
                   consent-collection
                   (.where "id" "=" user-id)))
           (fn [query-snap]
             (if (.-empty query-snap)
               (do
                 (h/dprn "Couldn't find consent for ")
                 (on-success false))
               (do
                 (h/dprn "Found consent")
                 (on-success true)))
             (fn [e]
               (h/dprn e)
               (on-failure)))))

  (accept-policies! [this user-id on-success on-failure]
    (.then
      (.add consent-collection
            (clj->js
              {:id user-id :date (t/now)}))
      on-success on-failure))

  (sign-in! [this email password on-success on-failure]
    (let [sign-in-chan (chan)]
      (.then (.signInAndRetrieveDataWithEmailAndPassword fb-auth email password)
             (partial put! sign-in-chan)
             (partial put! sign-in-chan))
      (go
        (let [result (<! sign-in-chan)]
          ;; #js {:additionalUserInfo #js {:isNewUser false}, :user #object[User [object Object]]}
          (h/dprn "Sign in returned " result)
          (if-let [user (.-user result)]
            (let [cljs-user (fb-user->cljs (.-user result))]
              ;; sign in handled by onauthstatechanged listener
              (on-success)
              (h/dprn "Signed in " cljs-user))
            (let [cljs-code (err->clj result)]
              (h/dprn "Sign in failed with code " cljs-code)
              (on-failure cljs-code)))))))

  (sign-in-social! [this provider on-success on-failure]
    (let [on-success (fn [user] (when user (on-success)))]
      (condp = provider
        :google (social/google-sign-in on-success on-failure)
        :facebook (social/facebook-sign-in on-success on-failure)
        (h/dprn :unknown-social-provider provider))))

  (sign-in-known-user! [this]
    (h/dprn "Search for known user")
    ;; firebase.auth().onAuthStateChanged((user) => {
    ;;this.setState({ loading: false, user });
    (.onAuthStateChanged fb-auth
                         (fn [user]
                           (when user
                             (h/dprn "Detected auth state change, set app-db and check policies")
                             (rf/dispatch-sync [:set-user (fb-user->cljs user)])
                             (rf/dispatch [:check-policy-consent])))))

  (sign-in-anonymously! [this on-success on-failure]
    (.then (.signInAnonymouslyAndRetrieveData (.auth fb))
           (fn [user]
             (on-success)
             (h/dprn "Signed in anonymously"))
           on-failure))

  (sign-up! [this email password on-success on-failure]
    (let [sign-up-chan (chan)]
      (.then (.createUserAndRetrieveDataWithEmailAndPassword fb-auth email password)
             (partial put! sign-up-chan)
             (partial put! sign-up-chan))
      (go
        (let [result (<! sign-up-chan)]
          (h/dprn "Sign up returned " result)
          (if-let [user (.-user result)]
            (let [cljs-user (fb-user->cljs (.-user result))]
              (on-success)
              (h/dprn "Signed up " cljs-user))
            (let [cljs-code (err->clj result)]
              (h/dprn "Sign up failed with code " cljs-code)
              (on-failure cljs-code)))))))

  (sign-out! [this]
    (.signOut fb-auth)))