# examples: sample business modules

Modules under `examples/` only demonstrate **how a business service plugs into the permission foundation**. They are **not part of the permission kernel**. Copy and adapt them, or delete the whole directory.

| Module | Description |
| --- | --- |
| [ping-order](ping-order/README.en.md) | Order sample: PC admin management + App user ordering, showing RBAC, dual login domains, endpoint permissions, auditing and an order state machine |

## Copy into a new business module

1. Copy `ping-order` to `ping-xxx`, then update its `pom.xml` `artifactId` / `name` / description;
2. Add `<module>ping-xxx</module>` to `examples/pom.xml`;
3. Rename packages, classes and tables, keeping only the starters you need (`ping-web` / `ping-dubbo` / `ping-log` / `ping-id`, etc.);
4. Register the new permission codes and API paths in permission management.

## Remove the samples

Delete `examples/`, the `<module>examples</module>` entry in the root `pom.xml`, the `order-service` block in `docker-compose.yml`, and the gateway `/order/**` route. The permission kernel is unaffected.
