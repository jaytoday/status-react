(ns status-im.contexts.profile.profiles.view
  (:require
    [native-module.core :as native-module]
    [quo.core :as quo]
    [react-native.core :as rn]
    [react-native.reanimated :as reanimated]
    [react-native.safe-area :as safe-area]
    [reagent.core :as reagent]
    [status-im.common.confirmation-drawer.view :as confirmation-drawer]
    [status-im.common.standard-authentication.core :as standard-authentication]
    [status-im.config :as config]
    [status-im.constants :as constants]
    [status-im.contexts.onboarding.common.background.view :as background]
    [status-im.contexts.profile.profiles.style :as style]
    [taoensso.timbre :as log]
    [utils.i18n :as i18n]
    [utils.re-frame :as rf]
    [utils.transforms :as transforms]))

(defonce push-animation-fn-atom (atom nil))
(defonce pop-animation-fn-atom (atom nil))
;; we need this for hotreload, overwise on hotreload translate-x will be reseted
(defonce translate-x-atom (atom 0))

(defn push-animation
  [translate-x]
  (let [window-width (:width (rn/get-window))]
    (reset! translate-x-atom (- window-width))
    (reanimated/animate-shared-value-with-delay translate-x
                                                (- window-width)
                                                constants/onboarding-modal-animation-duration
                                                :linear
                                                100)))

(defn pop-animation
  [translate-x]
  (reset! translate-x-atom 0)
  (reanimated/animate-shared-value-with-delay translate-x
                                              0
                                              constants/onboarding-modal-animation-duration
                                              :linear
                                              50))

(defn new-account-options
  []
  [quo/action-drawer
   [[{:icon                :i/profile
      :label               (i18n/label :t/create-new-profile)
      :on-press            (fn []
                             (when @push-animation-fn-atom
                               (@push-animation-fn-atom))
                             (rf/dispatch [:open-modal :new-to-status]))
      :accessibility-label :create-new-profile}
     {:icon                :i/multi-profile
      :label               (i18n/label :t/add-existing-status-profile)
      :on-press            #(rf/dispatch [:open-modal :sign-in])
      :accessibility-label :multi-profile}]]])

(defn show-new-account-options
  []
  (rf/dispatch [:show-bottom-sheet
                {:content new-account-options
                 :shell?  true}]))

(defn delete-profile-confirmation
  [key-uid context]
  [confirmation-drawer/confirmation-drawer
   {:title               (i18n/label :t/remove-profile?)
    :description         (i18n/label :t/remove-profile-confirm-message)
    :accessibility-label :remove-profile-confirm
    :context             context
    :button-text         (i18n/label :t/remove)
    :close-button-text   (i18n/label :t/cancel)
    :on-press            (fn []
                           (rf/dispatch [:hide-bottom-sheet])
                           (native-module/delete-multiaccount
                            key-uid
                            (fn [result]
                              (let [{:keys [error]} (transforms/json->clj result)]
                                (rf/dispatch [:onboarding/on-delete-profile-success key-uid])
                                (log/info "profile deleted: error" error)))))}])

(defn show-confirmation
  [key-uid context]
  (rf/dispatch [:show-bottom-sheet
                {:content (fn [] [delete-profile-confirmation key-uid context])}]))

(defn profile-options
  [key-uid context]
  [quo/action-drawer
   [[{:icon                :i/delete
      :label               (i18n/label :t/remove-profile-message)
      :on-press            #(show-confirmation key-uid context)
      :accessibility-label :remove-profile
      :danger?             true}]]])

(defn show-profile-options
  [key-uid context]
  (rf/dispatch [:show-bottom-sheet
                {:content (fn [] [profile-options key-uid context])
                 :shell?  true}]))

(defn profile-card
  [{:keys [name key-uid customization-color keycard-pairing]}
   index
   _
   {:keys [last-index set-hide-profiles]}]
  (let [last-item?      (= last-index index)
        profile-picture (rf/sub [:profile/login-profiles-picture key-uid])]
    [quo/profile-card
     {:name                 name
      :login-card?          true
      :last-item?           (= last-index index)
      :customization-color  (or customization-color :primary)
      :keycard-account?     keycard-pairing
      :show-options-button? true
      :profile-picture      profile-picture
      :card-style           (style/profiles-profile-card last-item?)
      :on-options-press     #(show-profile-options
                              key-uid
                              {:name            name
                               :color           customization-color
                               :profile-picture profile-picture})
      :on-card-press        (fn []
                              (rf/dispatch
                               [:profile/profile-selected key-uid])
                              (rf/dispatch
                               [:profile.login/login-with-biometric-if-available key-uid])
                              (when-not keycard-pairing (set-hide-profiles)))}]))

(defn- f-profiles-section
  [{:keys [set-hide-profiles]}]
  (let [profiles    (vals (rf/sub [:profile/profiles-overview]))
        translate-x (reanimated/use-shared-value @translate-x-atom)]
    (rn/use-effect (fn []
                     (reset! push-animation-fn-atom #(push-animation translate-x))
                     (reset! pop-animation-fn-atom #(pop-animation translate-x))
                     (fn []
                       (reset! push-animation-fn-atom nil)
                       (reset! pop-animation-fn-atom nil))))
    [reanimated/view
     {:style (style/profiles-container translate-x)}
     [rn/view
      {:style style/profiles-header}
      [quo/text
       {:size   :heading-1
        :weight :semi-bold
        :style  style/profiles-header-text}
       (i18n/label :t/profiles-on-device)]
      [quo/button
       {:type                :primary
        :customization-color :blue
        :size                32
        :icon-only?          true
        :on-press            show-new-account-options
        :accessibility-label :show-new-account-options}
       :main-icons/add]]
     [rn/flat-list
      {:data                    (sort-by :timestamp > profiles)
       :key-fn                  :key-uid
       :content-container-style {:padding-bottom 20}
       :render-data             {:last-index        (dec (count profiles))
                                 :set-hide-profiles set-hide-profiles}
       :render-fn               profile-card}]]))

(defn profiles-section
  [props]
  [:f> f-profiles-section props])

(defn login-section
  [{:keys [set-show-profiles]}]
  (let [{:keys [processing password]}              (rf/sub [:profile/login])
        {:keys [key-uid name customization-color]} (rf/sub [:profile/login-profile])
        sign-in-enabled?                           (rf/sub [:sign-in-enabled?])
        profile-picture                            (rf/sub [:profile/login-profiles-picture key-uid])
        auth-method                                (rf/sub [:auth-method])
        login-multiaccount                         #(rf/dispatch [:profile.login/login])]
    [rn/keyboard-avoiding-view
     {:style                  style/login-container
      :keyboardVerticalOffset (- (safe-area/get-bottom))}
     [rn/view
      {:style style/multi-profile-button-container}
      (when config/quo-preview-enabled?
        [quo/button
         {:size                32
          :type                :grey
          :background          :blur
          :icon-only?          true
          :on-press            #(rf/dispatch [:navigate-to :quo-preview])
          :disabled?           processing
          :accessibility-label :quo-preview
          :container-style     {:margin-right 12}}
         :i/reveal-whitelist])
      [quo/button
       {:size                32
        :type                :grey
        :background          :blur
        :icon-only?          true
        :on-press            set-show-profiles
        :disabled?           processing
        :accessibility-label :show-profiles}
       :i/multi-profile]]
     [rn/scroll-view
      {:keyboard-should-persist-taps :always
       :style                        {:flex 1}}
      [quo/profile-card
       {:name                name
        :customization-color (or customization-color :primary)
        :profile-picture     profile-picture
        :card-style          style/login-profile-card}]
      [standard-authentication/password-input
       {:shell?              true
        :blur?               true
        :on-press-biometrics (when (= auth-method constants/auth-method-biometric)
                               (fn []
                                 (rf/dispatch [:biometric/authenticate
                                               {:on-success #(rf/dispatch
                                                              [:profile.login/biometric-success])
                                                :on-fail    #(rf/dispatch
                                                              [:profile.login/biometric-auth-fail
                                                               %])}])))
        :default-password    password}]]
     [quo/button
      {:size                40
       :type                :primary
       :customization-color (or customization-color :primary)
       :accessibility-label :login-button
       :icon-left           :i/unlocked
       :disabled?           (or (not sign-in-enabled?) processing)
       :on-press            login-multiaccount
       :container-style     {:margin-bottom (+ (safe-area/get-bottom) 12)}}
      (i18n/label :t/log-in)]]))

;; we had to register it here, because of hotreload, overwise on hotreload it will be reseted
(defonce show-profiles? (reagent/atom false))

(defn view
  []
  (let [set-show-profiles #(reset! show-profiles? true)
        set-hide-profiles #(reset! show-profiles? false)]
    (fn []
      [:<>
       [background/view true]
       (if @show-profiles?
         [profiles-section {:set-hide-profiles set-hide-profiles}]
         [login-section {:set-show-profiles set-show-profiles}])])))
