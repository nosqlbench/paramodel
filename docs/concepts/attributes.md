# Attributes

There are a few different kinds of attributes in the hyperplane system. They all have a similar
structure

## Labels

Labels are the strictest form of attribute. They describe what something is intrinsically, or a
property of the thing which is structurally or semantically immutable.

You should be able to filter on labels, as in "what's in the package?", or "who owns it?". However,
you should not be able to modify the label.

`Labeled` is a contract type, and each name-value item within it is a Label contract type. An
object implementing the Labeled interface provides a named set of Label instances via `getLabels()`.

## Traits

Traits are a type of label which is used to establish structural relationships.

Traits are categoric and type-relational properties. For example you may need to have an upstream
service that is of type `mysql`. There is a "plug and socket" semantic to traits. You may say that
item _needs_ a connection to another item having trait `database:mysql` without knowing much about
the other needed item other than this simple _type qualification_. Then another item may provide
that trait, making it compatible with the requirement.

`Traits` is a contract type, and each name-value item within it is a `Trait` contract type. An
object implementing the Traits interface provides a named set of Trait instances via `getTraits()`.


## Tags

Tags are the most flexible form of attribute. Tags are exposed at the surface layer where users
live. Users add and remove tags, modify them as needed. This allows users to have a more
intuitive view of what they are working with, and to simplify and filter their views according
to their own preferences.

Tagged is a contract type, and each name-value item within it is a Tag contract type. An
object implementing the Tagged interface provides a named set of Tag instances via `getTags()`.

## Engine-opaque tiers on Element

The paramodel engine does **not** consume any specific trait or tag keys on `Element`. Both the
`Traits` and `Tagged` tiers on Element exist as adopter extension points — adopters may populate
them with system-specific metadata for their own downstream use. Any property that the paramodel
engine needs to act on must be modeled as a typed first-class method on `Element` (e.g.
`maxConcurrency()`, `shutdownSemantics()`, `trialElement()`), not as a stringly-typed attribute.

This invariant prevents the engine from silently depending on magic key names and keeps the
contract surface explicit and type-safe.

# Attribute Filtering

Attributed is a contract type, and the base contract type for Labeled, Traits, and Tagged.

The Attributed interface provides a `getAttributes()` method which returns a map of the
attribute types to their respective attribute instances.

## Namespaces

Since all the attribute types above provide a _property view_ of an item, there is some
disambiguation which is necessary. There are precedence rules for how attributes are combined.
There are rules for how attribute types may or may not be combined.

At any time, a single object may not have more than one attribute for a given name. This means
that at the Attributed level, when any Trait, Label, or Tag is added, if there is a conflict
with one of the other implemented attributes, an error should be thrown. This suggest some
common implementation concerns among the three types, possibly having a single internal
implementation with a common interface which indirects to its internal state for the purposes of
call-time safety checks.

## Syntax

Users should be allowed to filter items using the semantics and syntax specified here:
https://github.com/nosqlbench/nosqlbench/blob/f531ee9524ee9f0e530e81a8274c9b4cdd8bc882/nb-apis/nb-api/src/main/java/io/nosqlbench/nb/api/tagging/TagFilter.java
