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
package io.nosqlbench.paramodel.sims.elements;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.ElementProvider;

import java.util.List;

///
/// SPI provider that supplies the simulated element templates.
///
/// Discovered automatically via {@link java.util.ServiceLoader} when the
/// {@code paramodel-sims} module is on the module path.
///
/// @see DummyElement
/// @since 0.1.0
///
public class SimElementProvider implements ElementProvider {

    /// Creates a new {@code SimElementProvider}.
    public SimElementProvider() {}

    @Override
    public List<Element> elements() {
        return List.of(DummyElement.template());
    }
}
