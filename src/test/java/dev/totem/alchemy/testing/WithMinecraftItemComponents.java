package dev.totem.alchemy.testing;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt in explicitly to Minecraft 26.3's vanilla item component bootstrap.
 *
 * <p>Fabric Loader JUnit uses a separate Knot classloader and does not
 * discover our auto-registered Jupiter extension from test resources; the
 * explicit meta-annotation guarantees bootstrap in the same test loader as
 * ItemStack. Apply only to tests that actually touch runtime item holders.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MinecraftRegistryBootstrapExtension.class)
public @interface WithMinecraftItemComponents {
}
