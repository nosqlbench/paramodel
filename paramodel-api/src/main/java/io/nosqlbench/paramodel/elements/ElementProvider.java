/*
 * Copyright (c) nosqlbench
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nosqlbench.paramodel.elements;

import java.util.List;

///
/// Service provider interface for modules that supply element model templates.
///
/// Implementing modules register an {@code ElementProvider} via Java SPI
/// ({@link java.util.ServiceLoader}) so that host runtimes can discover
/// available element types without compile-time coupling.
///
/// ## Usage
///
/// ```java
/// // Discover all element providers on the module path
/// ServiceLoader<ElementProvider> providers = ServiceLoader.load(ElementProvider.class);
/// for (ElementProvider provider : providers) {
///     List<Element> templates = provider.elements();
///     // register templates with the runtime
/// }
/// ```
///
/// @see Element
/// @since 0.1.0
///
public interface ElementProvider {

    /// Returns the element model templates provided by this module.
    ///
    /// Each returned element serves as a canonical template with default
    /// parameter values for its element type. Host systems may clone or
    /// reconfigure these templates for specific studies.
    ///
    /// @return unmodifiable list of element templates, never null
    List<Element> elements();
}
