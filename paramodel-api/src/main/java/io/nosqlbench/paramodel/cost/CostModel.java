package io.nosqlbench.paramodel.cost;

///
/// # CostModel
///
/// Defines cost calculation model with pricing for different resource types.
///
public interface CostModel {

    double cpuRatePerCoreHour();

    double memoryRatePerGbHour();

    double storageRatePerGbHour();

    double networkRatePerGb();

    double elementRate(String elementType);

    static CostModel defaultModel() {
        throw new UnsupportedOperationException(
            "CostModel.defaultModel() requires a concrete implementation");
    }
}
