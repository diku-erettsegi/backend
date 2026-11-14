package hu.diku.api;

import java.time.LocalDate;

/** Separate verified settlement from the immutable submission quote. No automatic charging or invoicing. */
record FinancialSummary(boolean contractConfirmed,int paidTrainingTotal,LocalDate fullPaymentDate,int verifiedFriendCount,
    int discountPercent,int periodDiscountPercent,Integer trainingTotal,Integer laptopRentalFee,Integer trainingBalance,Integer minimumAdvanceTotal) {
    static FinancialSummary of(Application a) {
        if(a.baseTotal==null) return new FinancialSummary(a.contractConfirmed,a.paidTrainingTotal,a.fullPaymentDate,a.verifiedFriendCount,0,0,null,a.laptopRentalFee,null,null);
        int base=a.baseTotal;
        int friends=a.verifiedFriendCount*5;
        int period=0;
        if(a.fullPaymentDate!=null) {
            period=CoursePricing.price(base,80,a.fullPaymentDate,0,false).periodDiscountPercent();
            // Registration and complete payment must both fall within the promotion.
            period=Math.min(period,a.periodDiscountPercent==null?0:a.periodDiscountPercent);
            int candidate=total(base,Math.min(35,period+friends));
            if(a.paidTrainingTotal<candidate) period=0;
        }
        int percent=Math.min(35,period+friends), total=total(base,percent);
        return new FinancialSummary(a.contractConfirmed,a.paidTrainingTotal,a.fullPaymentDate,a.verifiedFriendCount,
            percent,period,total,a.laptopRentalFee,total-a.paidTrainingTotal,Math.ceilDiv(total,5));
    }
    private static int total(int base,int percent) { return Math.toIntExact(Math.round(base*(100-percent)/100.0)); }
}
