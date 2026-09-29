package com.no8do.api.integration;

public final class IntegrationBootstrapResult {
    private final IntegrationBootstrapState state;
    private final int interval;
    private final String integrationCredential;

    private IntegrationBootstrapResult(IntegrationBootstrapState state, int interval, String credential) {
        this.state = state;
        this.interval = interval;
        this.integrationCredential = credential;
    }
    static IntegrationBootstrapResult state(IntegrationBootstrapState state, int interval) {
        return new IntegrationBootstrapResult(state, interval, null);
    }
    static IntegrationBootstrapResult consumed(String credential) {
        return new IntegrationBootstrapResult(IntegrationBootstrapState.CONSUMED, 0, credential);
    }
    public IntegrationBootstrapState getState() { return state; }
    public int getInterval() { return interval; }
    public String getIntegrationCredential() { return integrationCredential; }
    @Override public String toString() {
        return "IntegrationBootstrapResult[state=" + state + ", credential="
                + (integrationCredential == null ? "absent" : "REDACTED") + "]";
    }
}
