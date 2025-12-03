package com.candid.api.inference;

/**
 * Represents insurance claim data for denial prediction.
 * Use the Builder to construct instances.
 */
public class ClaimData {

    private final double amount;
    private final int patientAge;
    private final String procedureCategory;
    private final String providerState;
    private final int priorDenialCount;

    private ClaimData(Builder builder) {
        this.amount = builder.amount;
        this.patientAge = builder.patientAge;
        this.procedureCategory = builder.procedureCategory;
        this.providerState = builder.providerState;
        this.priorDenialCount = builder.priorDenialCount;
    }

    public double getAmount() {
        return amount;
    }

    public int getPatientAge() {
        return patientAge;
    }

    public String getProcedureCategory() {
        return procedureCategory;
    }

    public String getProviderState() {
        return providerState;
    }

    public int getPriorDenialCount() {
        return priorDenialCount;
    }

    /**
     * Builder for constructing ClaimData instances.
     */
    public static class Builder {
        private double amount;
        private int patientAge;
        private String procedureCategory;
        private String providerState;
        private int priorDenialCount;

        public Builder setAmount(double amount) {
            this.amount = amount;
            return this;
        }

        public Builder setPatientAge(int patientAge) {
            this.patientAge = patientAge;
            return this;
        }

        public Builder setProcedureCategory(String procedureCategory) {
            this.procedureCategory = procedureCategory;
            return this;
        }

        public Builder setProviderState(String providerState) {
            this.providerState = providerState;
            return this;
        }

        public Builder setPriorDenialCount(int priorDenialCount) {
            this.priorDenialCount = priorDenialCount;
            return this;
        }

        public ClaimData build() {
            return new ClaimData(this);
        }
    }

    @Override
    public String toString() {
        return String.format("ClaimData{amount=%.2f, patientAge=%d, procedureCategory='%s', providerState='%s', priorDenials=%d}",
                amount, patientAge, procedureCategory, providerState, priorDenialCount);
    }
}
