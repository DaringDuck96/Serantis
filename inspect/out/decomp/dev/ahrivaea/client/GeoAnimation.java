package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;

public final class GeoAnimation {
   private final List<GeoAnimation.BoneTrack> tracks;
   private final double length;
   private final boolean loop;

   private GeoAnimation(List<GeoAnimation.BoneTrack> tracks, double length, boolean loop) {
      this.tracks = tracks;
      this.length = length;
      this.loop = loop;
   }

   public static Map<String, GeoAnimation> read(JsonObject file) {
      Map<String, GeoAnimation> out = new HashMap<>();
      JsonObject anims = file.getAsJsonObject("animations");
      if (anims == null) {
         return out;
      } else {
         for (Entry<String, JsonElement> entry : anims.entrySet()) {
            String name = entry.getKey().substring(entry.getKey().lastIndexOf(46) + 1);
            JsonObject a = entry.getValue().getAsJsonObject();
            List<GeoAnimation.BoneTrack> tracks = new ArrayList<>();
            JsonObject bones = a.getAsJsonObject("bones");
            if (bones != null) {
               for (Entry<String, JsonElement> b : bones.entrySet()) {
                  JsonObject o = b.getValue().getAsJsonObject();
                  tracks.add(new GeoAnimation.BoneTrack(b.getKey(), channel(o.get("rotation")), channel(o.get("position"))));
               }
            }

            double length = a.has("animation_length") ? a.get("animation_length").getAsDouble() : 0.0;
            boolean loop = a.has("loop") && a.get("loop").isJsonPrimitive() && a.get("loop").getAsJsonPrimitive().isBoolean() && a.get("loop").getAsBoolean();
            out.put(name, new GeoAnimation(tracks, length, loop));
         }

         return out;
      }
   }

   private static GeoAnimation.Channel channel(JsonElement e) {
      if (e == null) {
         return null;
      } else {
         TreeMap<Double, Molang.Expr[]> frames = new TreeMap<>();
         if (e.isJsonObject()) {
            for (Entry<String, JsonElement> f : e.getAsJsonObject().entrySet()) {
               JsonElement v = f.getValue();
               if (v.isJsonObject()) {
                  v = v.getAsJsonObject().has("post") ? v.getAsJsonObject().get("post") : v.getAsJsonObject().get("pre");
               }

               frames.put(Double.parseDouble(f.getKey()), vec(v));
            }
         } else {
            frames.put(0.0, vec(e));
         }

         return frames.isEmpty() ? null : new GeoAnimation.Channel(frames);
      }
   }

   private static Molang.Expr[] vec(JsonElement v) {
      if (v != null && v.isJsonArray()) {
         JsonArray a = v.getAsJsonArray();
         return new Molang.Expr[]{expr(a.get(0)), expr(a.get(1)), expr(a.get(2))};
      } else {
         Molang.Expr one = expr(v);
         return new Molang.Expr[]{one, one, one};
      }
   }

   private static Molang.Expr expr(JsonElement v) {
      if (v == null || v.isJsonNull()) {
         return Molang.ZERO;
      } else if (v.getAsJsonPrimitive().isNumber()) {
         double d = v.getAsDouble();
         return t -> d;
      } else {
         return Molang.parse(v.getAsString());
      }
   }

   public void apply(Function<String, ModelPart> bones, double seconds) {
      double t = seconds;
      if (this.length > 0.0) {
         t = this.loop ? seconds % this.length : Math.min(seconds, this.length);
      }

      for (GeoAnimation.BoneTrack track : this.tracks) {
         ModelPart p = bones.apply(track.bone());
         if (p != null) {
            if (track.rotation() != null) {
               double[] r = track.rotation().at(t);
               p.xRot = p.xRot + (float)(-r[0]) * (float) (Math.PI / 180.0);
               p.yRot = p.yRot + (float)(-r[1]) * (float) (Math.PI / 180.0);
               p.zRot = p.zRot + (float)r[2] * (float) (Math.PI / 180.0);
            }

            if (track.position() != null) {
               double[] o = track.position().at(t);
               p.x = p.x + (float)(-o[0]);
               p.y = p.y + (float)(-o[1]);
               p.z = p.z + (float)o[2];
            }
         }
      }
   }

   private static record BoneTrack(String bone, GeoAnimation.Channel rotation, GeoAnimation.Channel position) {
   }

   private static record Channel(TreeMap<Double, Molang.Expr[]> frames) {
      double[] at(double t) {
         Entry<Double, Molang.Expr[]> lo = this.frames.floorEntry(t);
         Entry<Double, Molang.Expr[]> hi = this.frames.ceilingEntry(t);
         if (lo == null) {
            lo = hi;
         }

         if (hi == null) {
            hi = lo;
         }

         double[] a = eval(lo.getValue(), t);
         if (hi.getKey().equals(lo.getKey())) {
            return a;
         } else {
            double[] b = eval(hi.getValue(), t);
            double f = (t - lo.getKey()) / (hi.getKey() - lo.getKey());
            return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
         }
      }

      static double[] eval(Molang.Expr[] e, double t) {
         return new double[]{e[0].eval(t), e[1].eval(t), e[2].eval(t)};
      }
   }
}
