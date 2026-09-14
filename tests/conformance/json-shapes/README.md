# JSHAPE independent conformance data

`catalog.tsv` fixes all 40 public IDs. `vectors.tsv` describes JSON instances and compact
implementation-independent shape trees used only by the oracle. `resources.tsv` fixes every
finite boundary and failure cap. Production code does not read these files.

`chapter/json-shapes-chapter.bsb` is the executable reference program. Its fixed stdout artifact
also verifies explicit access to the four closed failure fields.
