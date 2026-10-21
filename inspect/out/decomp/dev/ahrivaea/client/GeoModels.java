package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.Ahrivaea;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.model.geom.ModelPart.Polygon;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3fc;

public final class GeoModels {
   private static Map<String, GeoCreatureModel> models = Map.of();
   private static Map<String, Float> extents = Map.of();
   private static Map<String, Float> scales = Map.of();
   private static Set<String> textures = Set.of();
   private static Map<String, Float> heights = Map.of();
   private static Set<String> realSize = Set.of();
   public static final Identifier VILLAGER_SKIN = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

   private GeoModels() {
   }

   public static void reload(ResourceManager manager) {
      Map<String, GeoCreatureModel> loadedModels = new HashMap<>();
      Map<String, Float> loadedExtents = new HashMap<>();
      Map<String, Float> loadedScales = new HashMap<>();
      Map<String, Float> loadedHeights = new HashMap<>();
      Set<String> loadedRealSize = new HashSet<>();
      Map<String, Map<String, GeoAnimation>> loadedAnimations = new HashMap<>();
      manager.listResources("geo/creature", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
         String rest = path.getPath().substring("geo/creature/".length());
         int slash = rest.indexOf(47);
         if (slash >= 0) {
            String species = rest.substring(0, slash);
            String file = rest.substring(slash + 1);

            try (Reader reader = resource.openAsReader()) {
               JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
               if (file.equals("settings.json")) {
                  if (json.has("model_scale")) {
                     loadedScales.put(species, json.get("model_scale").getAsFloat());
                  }
               } else if (file.endsWith(".geo.json")) {
                  String key = species + "/" + file.substring(0, file.length() - ".geo.json".length());
                  loadedModels.put(key, new GeoCreatureModel(build(json)));
                  loadedExtents.put(key, extent(json));
                  loadedHeights.put(key, height(json));
                  JsonObject desc = json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonObject("description");
                  if (desc != null && desc.has("ahrivaea:real_size") && desc.get("ahrivaea:real_size").getAsBoolean()) {
                     loadedRealSize.add(key);
                  }
               } else if (file.endsWith(".animation.json")) {
                  loadedAnimations.put(species + "/" + file.substring(0, file.length() - ".animation.json".length()), GeoAnimation.read(json));
               }
            } catch (Exception var18) {
               Ahrivaea.LOGGER.error("Could not load creature model {}: {}", path, var18.toString());
            }
         }
      });
      Set<String> loadedTextures = new HashSet<>();
      manager.listResources("textures/entity/creature", path -> path.getPath().endsWith(".png")).keySet().forEach(path -> {
         String p = path.getPath().substring("textures/entity/creature/".length());
         loadedTextures.add(p.substring(0, p.length() - ".png".length()));
      });
      loadedAnimations.forEach((key, anims) -> {
         GeoCreatureModel m = loadedModels.get(key);
         if (m != null) {
            m.setAnimations((Map<String, GeoAnimation>)anims);
         }
      });
      models = Map.copyOf(loadedModels);
      heights = Map.copyOf(loadedHeights);
      realSize = Set.copyOf(loadedRealSize);
      extents = Map.copyOf(loadedExtents);
      scales = Map.copyOf(loadedScales);
      textures = Set.copyOf(loadedTextures);
      Ahrivaea.LOGGER.info("Loaded {} creature models: {}", models.size(), String.join(", ", new TreeSet<>(models.keySet())));
   }

   public static GeoCreatureModel model(String species, String influence) {
      String key = key(species, influence);
      return key == null ? null : models.get(key);
   }

   private static String key(String species, String influence) {
      String own = species + "/" + influence;
      if (models.containsKey(own)) {
         return own;
      } else if (models.containsKey(species + "/none")) {
         return species + "/none";
      } else {
         String prefix = species + "/";
         String any = null;

         for (String k : models.keySet()) {
            if (k.startsWith(prefix) && (any == null || k.compareTo(any) < 0)) {
               any = k;
            }
         }

         return any;
      }
   }

   public static float extent(String species, String influence) {
      String key = key(species, influence);
      return key == null ? 1.0F : extents.getOrDefault(key, 1.0F);
   }

   private static float extent(JsonObject file) {
      float minX = Float.MAX_VALUE;
      float maxX = -Float.MAX_VALUE;
      float minZ = Float.MAX_VALUE;
      float maxZ = -Float.MAX_VALUE;
      JsonObject geo = file.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();

      for (JsonElement e : geo.getAsJsonArray("bones")) {
         JsonObject b = e.getAsJsonObject();
         if (b.has("cubes")) {
            for (JsonElement ce : b.getAsJsonArray("cubes")) {
               float[] o = vec(ce.getAsJsonObject(), "origin");
               float[] sz = vec(ce.getAsJsonObject(), "size");
               minX = Math.min(minX, o[0]);
               maxX = Math.max(maxX, o[0] + sz[0]);
               minZ = Math.min(minZ, o[2]);
               maxZ = Math.max(maxZ, o[2] + sz[2]);
            }
         }
      }

      return minX > maxX ? 1.0F : Math.max(maxX - minX, maxZ - minZ) / 16.0F;
   }

   public static float height(String species, String influence) {
      String key = key(species, influence);
      return key == null ? 1.0F : heights.getOrDefault(key, 1.0F);
   }

   public static boolean realSize(String species, String influence) {
      String key = key(species, influence);
      return key != null && realSize.contains(key);
   }

   private static float height(JsonObject file) {
      float maxY = 0.0F;
      JsonObject geo = file.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();

      for (JsonElement e : geo.getAsJsonArray("bones")) {
         JsonObject b = e.getAsJsonObject();
         if (b.has("cubes")) {
            for (JsonElement ce : b.getAsJsonArray("cubes")) {
               float[] o = vec(ce.getAsJsonObject(), "origin");
               float[] sz = vec(ce.getAsJsonObject(), "size");
               maxY = Math.max(maxY, o[1] + sz[1]);
            }
         }
      }

      return maxY > 0.0F ? maxY / 16.0F : 1.0F;
   }

   public static float scale(String species, String influence) {
      return realSize(species, influence) ? 1.0F : scale(species);
   }

   public static float scale(String species) {
      return scales.getOrDefault(species, 1.0F);
   }

   public static Identifier texture(String species, String influence, String variant) {
      if (!variant.equals("none")) {
         String key = key(species, influence);

         for (String k : new String[]{key, species + "/" + influence}) {
            if (k != null && textures.contains(k + "_" + variant)) {
               return Ahrivaea.id("textures/entity/creature/" + k + "_" + variant + ".png");
            }
         }
      }

      return texture(species, influence);
   }

   public static Identifier texture(String species, String influence) {
      if (species.equals("villager")) {
         return VILLAGER_SKIN;
      } else {
         String key = key(species, influence);
         if (key != null && textures.contains(key)) {
            return Ahrivaea.id("textures/entity/creature/" + key + ".png");
         } else if (textures.contains(species + "/" + influence)) {
            return Ahrivaea.id("textures/entity/creature/" + species + "/" + influence + ".png");
         } else {
            return textures.contains(species + "/none")
               ? Ahrivaea.id("textures/entity/creature/" + species + "/none.png")
               : Ahrivaea.id("textures/entity/creature/placeholder.png");
         }
      }
   }

   static ModelPart build(JsonObject file) {
      JsonObject geo = file.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
      JsonObject desc = geo.getAsJsonObject("description");
      int texW = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
      int texH = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;
      Map<String, JsonObject> bones = new LinkedHashMap<>();
      Map<String, List<String>> children = new HashMap<>();
      List<String> roots = new ArrayList<>();

      for (JsonElement e : geo.getAsJsonArray("bones")) {
         JsonObject b = e.getAsJsonObject();
         bones.put(b.get("name").getAsString(), b);
      }

      for (Entry<String, JsonObject> entry : bones.entrySet()) {
         JsonObject b = entry.getValue();
         String parent = b.has("parent") ? b.get("parent").getAsString() : null;
         if (parent != null && bones.containsKey(parent)) {
            children.computeIfAbsent(parent, k -> new ArrayList<>()).add(entry.getKey());
         } else {
            roots.add(entry.getKey());
         }
      }

      Map<String, ModelPart> top = new LinkedHashMap<>();

      for (String r : roots) {
         top.put(r, bone(r, null, bones, children, texW, texH, 0));
      }

      return new ModelPart(List.of(), top);
   }

   private static ModelPart bone(
      String name, JsonObject parentBone, Map<String, JsonObject> bones, Map<String, List<String>> children, int texW, int texH, int depth
   ) {
      JsonObject b = bones.get(name);
      float[] p = vec(b, "pivot");
      float[] pp = parentBone != null ? vec(parentBone, "pivot") : null;
      float jx = -p[0];
      float jy = 24.0F - p[1];
      float jz = p[2];
      float ox = pp == null ? jx : jx + pp[0];
      float oy = pp == null ? jy : jy - (24.0F - pp[1]);
      float oz = pp == null ? jz : jz - pp[2];
      float[] r = vec(b, "rotation");
      List<Cube> cubes = new ArrayList<>();
      if (b.has("cubes")) {
         for (JsonElement ce : b.getAsJsonArray("cubes")) {
            cubes.add(cube(ce.getAsJsonObject(), jx, jy, jz, texW, texH));
         }
      }

      Map<String, ModelPart> kids = new LinkedHashMap<>();
      if (depth < 64) {
         for (String c : children.getOrDefault(name, List.of())) {
            kids.put(c, bone(c, b, bones, children, texW, texH, depth + 1));
         }
      }

      ModelPart part = new ModelPart(cubes, kids);
      PartPose pose = PartPose.offsetAndRotation(
         ox, oy, oz, -r[0] * (float) (Math.PI / 180.0), -r[1] * (float) (Math.PI / 180.0), r[2] * (float) (Math.PI / 180.0)
      );
      part.setInitialPose(pose);
      part.loadPose(pose);
      return part;
   }

   private static String bedrockFace(Direction d) {
      return switch (d) {
         case DOWN -> "up";
         case UP -> "down";
         case WEST -> "east";
         case EAST -> "west";
         case NORTH -> "north";
         case SOUTH -> "south";
         default -> throw new MatchException(null, null);
      };
   }

   private static Cube cube(JsonObject c, float jx, float jy, float jz, int texW, int texH) {
      float[] o = vec(c, "origin");
      float[] s = vec(c, "size");
      float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0.0F;
      boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
      float minX = -(o[0] + s[0]) - jx;
      float minY = 24.0F - o[1] - s[1] - jy;
      float minZ = o[2] - jz;
      JsonElement uv = c.get("uv");
      if (uv != null && !uv.isJsonArray()) {
         JsonObject faces = uv.getAsJsonObject();
         Set<Direction> visible = EnumSet.noneOf(Direction.class);

         for (Direction d : Direction.values()) {
            if (faces.has(bedrockFace(d))) {
               visible.add(d);
            }
         }

         Cube cube = new Cube(0, 0, minX, minY, minZ, s[0], s[1], s[2], inflate, inflate, inflate, false, (float)texW, (float)texH, visible);

         for (int i = 0; i < cube.polygons.length; i++) {
            Polygon poly = cube.polygons[i];
            Vector3fc n = poly.normal();
            Direction dx = Direction.getApproximateNearest(n.x(), n.y(), n.z());
            JsonObject f = faces.getAsJsonObject(bedrockFace(dx));
            if (f != null) {
               float[] at = vec2(f, "uv");
               float[] size = vec2(f, "uv_size");
               float u1 = at[0];
               float v1 = at[1];
               float u2 = at[0] + size[0];
               float v2 = at[1] + size[1];
               float eu = Math.signum(u2 - u1) * 0.02F;
               float ev = Math.signum(v2 - v1) * 0.02F;
               cube.polygons[i] = new Polygon(poly.vertices(), u1 + eu, v1 + ev, u2 - eu, v2 - ev, (float)texW, (float)texH, false, dx);
            }
         }

         return cube;
      } else {
         int u = uv == null ? 0 : uv.getAsJsonArray().get(0).getAsInt();
         int v = uv == null ? 0 : uv.getAsJsonArray().get(1).getAsInt();
         return new Cube(u, v, minX, minY, minZ, s[0], s[1], s[2], inflate, inflate, inflate, mirror, (float)texW, (float)texH, EnumSet.allOf(Direction.class));
      }
   }

   private static float[] vec2(JsonObject o, String key) {
      if (!o.has(key)) {
         return new float[2];
      } else {
         JsonArray a = o.getAsJsonArray(key);
         return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()};
      }
   }

   private static float[] vec(JsonObject o, String key) {
      if (!o.has(key)) {
         return new float[3];
      } else {
         JsonArray a = o.getAsJsonArray(key);
         return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
      }
   }
}
