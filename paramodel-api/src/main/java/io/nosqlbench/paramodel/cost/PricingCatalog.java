package io.nosqlbench.paramodel.cost;

import java.util.Optional;

///
/// # PricingCatalog
///
/// Catalog of pricing information for elements and resources.
///
public interface PricingCatalog {

    static PricingCatalog create() {
        throw new UnsupportedOperationException(
            "PricingCatalog.create() requires a concrete implementation");
    }

    Optional<Double> getPrice(String elementType, String region);

    void setPrice(String elementType, String region, double pricePerHour);
}
