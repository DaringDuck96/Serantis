package dev.ahrivaea.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.party.CaughtCreature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record Worker(CaughtCreature creature, UUID entity, List<String> order, List<String> off) {
   public static final Codec<Worker> CODEC = RecordCodecBuilder.create(
      i -> i.group(
               CaughtCreature.CODEC.fieldOf("creature").forGetter(Worker::creature),
               UUIDUtil.CODEC.fieldOf("entity").forGetter(Worker::entity),
               Codec.STRING.listOf().optionalFieldOf("job_order", List.of()).forGetter(Worker::order),
               Codec.STRING.listOf().optionalFieldOf("jobs_off", List.of()).forGetter(Worker::off)
            )
            .apply(i, Worker::new)
   );
   public static final String SILK_TOUCH = "silk_touch";

   public Worker(CaughtCreature creature, UUID entity, List<String> order, List<String> off) {
      order = List.copyOf(order);
      off = List.copyOf(off);
      this.creature = creature;
      this.entity = entity;
      this.order = order;
      this.off = off;
   }

   public Worker(CaughtCreature creature, UUID entity) {
      this(creature, entity, List.of(), List.of());
   }

   public Worker withCreature(CaughtCreature c) {
      return new Worker(c, this.entity, this.order, this.off);
   }

   public Worker withEntity(UUID e) {
      return new Worker(this.creature, e, this.order, this.off);
   }

   public List<Job> ordered(List<Job> jobs) {
      List<Job> out = new ArrayList<>(jobs);
      out.sort(Comparator.comparingInt(j -> {
         int k = this.order.indexOf(j.getSerializedName());
         return k < 0 ? this.order.size() + jobs.indexOf(j) : k;
      }));
      return out;
   }

   public List<Job> active(List<Job> jobs) {
      List<Job> out = this.ordered(jobs);
      out.removeIf(j -> this.off.contains(j.getSerializedName()));
      return out;
   }

   public Worker toggled(Job job) {
      return this.toggled(job.getSerializedName());
   }

   public Worker toggled(String setting) {
      List<String> o = new ArrayList<>(this.off);
      if (!o.remove(setting)) {
         o.add(setting);
      }

      return new Worker(this.creature, this.entity, this.order, o);
   }

   public Worker moved(List<Job> jobs, Job job, int by) {
      List<Job> now = this.ordered(jobs);
      int i = now.indexOf(job);
      int j = i + by;
      if (i >= 0 && j >= 0 && j < now.size()) {
         now.remove(i);
         now.add(j, job);
         return new Worker(this.creature, this.entity, now.stream().map(Job::getSerializedName).toList(), this.off);
      } else {
         return this;
      }
   }
}
