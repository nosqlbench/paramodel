///
/// Simulated element implementations for development and testing.
///
/// This module provides configurable dummy elements that mimic real
/// infrastructure behavior without interacting with actual systems.
/// Including this module as a dependency automatically makes simulated
/// element types discoverable via Java SPI.
///
/// @since 0.1.0
///
module io.nosqlbench.paramodel.sims {
    requires transitive io.nosqlbench.paramodel;

    exports io.nosqlbench.paramodel.sims.elements;

    provides io.nosqlbench.paramodel.elements.ElementProvider
        with io.nosqlbench.paramodel.sims.elements.SimElementProvider;

    provides io.nosqlbench.paramodel.elements.ElementTypeDescriptorProvider
        with io.nosqlbench.paramodel.sims.elements.SimElementTypeDescriptorProvider;
}
