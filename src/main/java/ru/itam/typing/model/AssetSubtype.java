package ru.itam.typing.model;

public enum AssetSubtype {
    SERVER,
    WORKSTATION,
    NETWORK_DEVICE,
    USER_ACCOUNT,
    SERVICE_ACCOUNT,

    // Software taxonomy. SECURITY_SOFTWARE and APPLICATION_SOFTWARE are retained
    // for backwards compatibility with the original benchmark rules.
    OPERATING_SYSTEM,
    OFFICE_SOFTWARE,
    BUSINESS_SOFTWARE,
    BROWSER,
    IDE,
    DATABASE_TOOL,
    DATABASE_SERVER,
    DESIGN_MODELING,
    SECURITY_SOFTWARE,
    CRYPTO_SOFTWARE,
    RUNTIME_PLATFORM,
    DEV_TOOL,
    UTILITY,
    COMMUNICATION,
    COMPONENT_AGENT,
    APPLICATION_SOFTWARE
}
