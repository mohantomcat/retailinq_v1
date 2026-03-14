import {createContext, useContext, useEffect, useMemo, useState} from 'react'
import {LANGUAGES, translations} from '../i18n/translations'
import {useAuth} from './AuthContext'
import {
    DEFAULT_ALLOWED_LANGUAGES,
    LANGUAGE_CATALOG as LANGUAGE_META,
} from '../i18n/languageCatalog'

const LANGUAGE_KEY = 'recon_ui_language'

const I18nContext = createContext(null)

function normalizeLanguage(value) {
    const normalized = String(value || '').trim().toLowerCase()

    if (
        normalized === 'te' ||
        normalized === 'telugu' ||
        normalized.includes('తెల')
    ) {
        return 'te'
    }

    if (
        normalized === 'ar' ||
        normalized === 'arabic' ||
        normalized.includes('الع')
    ) {
        return 'ar'
    }

    return 'en'
}

function interpolate(template, vars = {}) {
    return String(template).replace(/\{(\w+)\}/g, (_, key) =>
        vars[key] ?? `{${key}}`
    )
}

export function I18nProvider({children}) {
    const {user, updateUserPreferences} = useAuth()
    const [language, setLanguage] = useState(() =>
        normalizeLanguage(localStorage.getItem(LANGUAGE_KEY))
    )
    const allowedLanguages = useMemo(() => {
        const list = user?.allowedLanguages?.length
            ? user.allowedLanguages
            : DEFAULT_ALLOWED_LANGUAGES

        return Array.from(new Set(list.map(normalizeLanguage))).filter(
            Boolean
        )
    }, [user?.allowedLanguages])

    useEffect(() => {
        const userPreferred = normalizeLanguage(user?.preferredLanguage)
        const nextLanguage = allowedLanguages.includes(userPreferred)
            ? userPreferred
            : allowedLanguages.includes(language)
                ? language
                : allowedLanguages[0]

        if (nextLanguage && nextLanguage !== language) {
            setLanguage(nextLanguage)
        }
    }, [allowedLanguages, language, user?.preferredLanguage])

    useEffect(() => {
        const meta = LANGUAGE_META[language] || LANGUAGE_META.en

        localStorage.setItem(LANGUAGE_KEY, language)
        document.documentElement.setAttribute('data-recon-language', language)
        document.documentElement.setAttribute('lang', language)
        document.documentElement.setAttribute('dir', 'ltr')
        document.body?.setAttribute('dir', 'ltr')
        document.documentElement.setAttribute(
            'data-recon-content-dir',
            meta.direction
        )
    }, [language])

    const handleSetLanguage = (nextLanguage) => {
        const normalized = normalizeLanguage(nextLanguage)
        if (!allowedLanguages.includes(normalized)) return

        setLanguage(normalized)

        if (user?.preferredLanguage !== normalized) {
            updateUserPreferences?.({preferredLanguage: normalized})
        }
    }

    const value = useMemo(() => {
        const languageMeta = LANGUAGE_META[language] || LANGUAGE_META.en
        const dictionary = translations[language] || {}

        return {
            language,
            languageLabel: languageMeta.nativeLabel,
            languageMeta,
            setLanguage: handleSetLanguage,
            languages: LANGUAGES,
            languageOptions: LANGUAGE_META,
            allowedLanguages,
            direction: languageMeta.direction,
            isRTL: languageMeta.direction === 'rtl',
            t: (message, vars) =>
                interpolate(dictionary[message] || message, vars),
        }
    }, [allowedLanguages, language, updateUserPreferences, user?.preferredLanguage])

    return (
        <I18nContext.Provider value={value}>
            {children}
        </I18nContext.Provider>
    )
}

export function useI18n() {
    const context = useContext(I18nContext)

    if (!context) {
        throw new Error('useI18n must be used within I18nProvider')
    }

    return context
}
