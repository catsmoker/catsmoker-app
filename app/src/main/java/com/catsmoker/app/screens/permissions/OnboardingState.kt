package com.catsmoker.app.screens.permissions

/** Pending choices are not consent or Android grants. Only explicit actions apply them. */
data class OnboardingState(
    val step: Step = Step.WELCOME,
    val agreed: Boolean = false,
    /** A checked draft is not an applied analytics choice. */
    val analytics: Boolean = DEFAULT_ANALYTICS_SELECTION,
    val inventory: Boolean = true,
    val usage: Boolean = true,
    val notifications: Boolean = true,
) {
    enum class Step { WELCOME, TERMS, PERMISSIONS }

    fun back(): OnboardingState = copy(step = when (step) {
        Step.WELCOME -> Step.WELCOME
        Step.TERMS -> Step.WELCOME
        Step.PERMISSIONS -> Step.TERMS
    })

    fun continueWelcome(): OnboardingState = copy(step = Step.TERMS)
    fun continueTerms(): OnboardingState =
        if (step == Step.TERMS && agreed) {
            copy(step = Step.PERMISSIONS)
        } else this
    fun canFinish(): Boolean = step == Step.PERMISSIONS && agreed

    companion object {
        const val DEFAULT_ANALYTICS_SELECTION = true
    }
}
