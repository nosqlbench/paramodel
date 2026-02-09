///
/// Pre-built virtual element definitions for testing and demonstration.
///
/// This package provides a set of realistic mock elements that model a
/// simple infrastructure dependency graph:
///
/// ```
///   node (compute instance)
///     └── depends on ──▶ daemon (service process)
///                           └── depends on ──▶ dataset (static data resource)
/// ```
///
/// Each element carries typed parameters with real domains and constraints,
/// health check specifications, and instancing scopes. These definitions
/// exercise the full {@link io.nosqlbench.paramodel.elements.Element} API
/// surface and are suitable for use in integration tests, examples, and
/// TCK implementations.
///
/// ## Entry Point
///
/// Use {@link io.nosqlbench.paramodel.mock.elements.VirtualElements} to obtain
/// pre-built elements:
///
/// ```java
/// Element dataset = VirtualElements.dataset();
/// Element daemon  = VirtualElements.daemon();
/// Element node    = VirtualElements.node();
/// List<Element> all = VirtualElements.all();
/// ```
///
/// @see io.nosqlbench.paramodel.mock.elements.VirtualElements
/// @see io.nosqlbench.paramodel.mock.elements.MockHealthCheckSpec
/// @since 0.1.0
///
package io.nosqlbench.paramodel.mock.elements;
