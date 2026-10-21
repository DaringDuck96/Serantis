package dev.ahrivaea.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Molang {
   public static final Molang.Expr ZERO = t -> 0.0;

   private Molang() {
   }

   public static Molang.Expr parse(String text) {
      if (text != null && !text.isBlank()) {
         try {
            double constant = Double.parseDouble(text.trim());
            return t -> constant;
         } catch (NumberFormatException var4) {
            try {
               Molang.Parser p = new Molang.Parser(text.toLowerCase(Locale.ROOT).replace(" ", ""));
               Molang.Expr e = p.sum();
               return p.pos == p.s.length() ? e : ZERO;
            } catch (RuntimeException var3) {
               return ZERO;
            }
         }
      } else {
         return ZERO;
      }
   }

   @FunctionalInterface
   public interface Expr {
      double eval(double var1);
   }

   private static final class Parser {
      final String s;
      int pos;

      Parser(String s) {
         this.s = s;
      }

      boolean eat(char c) {
         if (this.pos < this.s.length() && this.s.charAt(this.pos) == c) {
            this.pos++;
            return true;
         } else {
            return false;
         }
      }

      Molang.Expr sum() {
         Molang.Expr e = this.product();

         while (true) {
            while (!this.eat('+')) {
               if (!this.eat('-')) {
                  return e;
               }

               Molang.Expr a = e;
               Molang.Expr b = this.product();
               e = t -> a.eval(t) - b.eval(t);
            }

            Molang.Expr a = e;
            Molang.Expr b = this.product();
            e = t -> a.eval(t) + b.eval(t);
         }
      }

      Molang.Expr product() {
         Molang.Expr e = this.unary();

         while (true) {
            while (!this.eat('*')) {
               if (!this.eat('/')) {
                  return e;
               }

               Molang.Expr a = e;
               Molang.Expr b = this.unary();
               e = t -> {
                  double d = b.eval(t);
                  return d == 0.0 ? 0.0 : a.eval(t) / d;
               };
            }

            Molang.Expr a = e;
            Molang.Expr b = this.unary();
            e = t -> a.eval(t) * b.eval(t);
         }
      }

      Molang.Expr unary() {
         if (this.eat('-')) {
            Molang.Expr a = this.unary();
            return t -> -a.eval(t);
         } else {
            return this.eat('+') ? this.unary() : this.primary();
         }
      }

      Molang.Expr primary() {
         if (this.eat('(')) {
            Molang.Expr e = this.sum();
            if (!this.eat(')')) {
               throw new IllegalArgumentException("missing )");
            } else {
               return e;
            }
         } else {
            int start = this.pos;
            char c = this.s.charAt(this.pos);
            if (!Character.isDigit(c) && c != '.') {
               while (
                  this.pos < this.s.length()
                     && (Character.isLetterOrDigit(this.s.charAt(this.pos)) || this.s.charAt(this.pos) == '_' || this.s.charAt(this.pos) == '.')
               ) {
                  this.pos++;
               }

               String name = this.s.substring(start, this.pos);
               if (name.isEmpty()) {
                  throw new IllegalArgumentException("unexpected " + c);
               } else if (!this.eat('(')) {
                  return switch (name) {
                     case "query.life_time", "q.life_time", "query.anim_time", "q.anim_time" -> t -> t;
                     case "math.pi" -> t -> Math.PI;
                     default -> Molang.ZERO;
                  };
               } else {
                  List<Molang.Expr> args = new ArrayList<>();
                  if (!this.eat(')')) {
                     do {
                        args.add(this.sum());
                     } while (this.eat(','));

                     if (!this.eat(')')) {
                        throw new IllegalArgumentException("missing )");
                     }
                  }

                  return call(name, args);
               }
            } else {
               while (this.pos < this.s.length() && (Character.isDigit(this.s.charAt(this.pos)) || this.s.charAt(this.pos) == '.')) {
                  this.pos++;
               }

               double v = Double.parseDouble(this.s.substring(start, this.pos));
               return t -> v;
            }
         }
      }

      static Molang.Expr call(String name, List<Molang.Expr> a) {
         Molang.Expr x = a.isEmpty() ? Molang.ZERO : a.get(0);
         Molang.Expr y = a.size() > 1 ? a.get(1) : Molang.ZERO;
         Molang.Expr z = a.size() > 2 ? a.get(2) : Molang.ZERO;

         return switch (name) {
            case "math.sin" -> t -> Math.sin(Math.toRadians(x.eval(t)));
            case "math.cos" -> t -> Math.cos(Math.toRadians(x.eval(t)));
            case "math.abs" -> t -> Math.abs(x.eval(t));
            case "math.sqrt" -> t -> Math.sqrt(Math.max(0.0, x.eval(t)));
            case "math.min" -> t -> Math.min(x.eval(t), y.eval(t));
            case "math.max" -> t -> Math.max(x.eval(t), y.eval(t));
            case "math.clamp" -> t -> Math.clamp(x.eval(t), y.eval(t), z.eval(t));
            case "math.mod" -> t -> {
            double d = y.eval(t);
            return d == 0.0 ? 0.0 : x.eval(t) % d;
         };
            default -> Molang.ZERO;
         };
      }
   }
}
