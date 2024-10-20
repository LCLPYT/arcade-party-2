package work.lclpnet.ap2.type;

import com.mojang.serialization.Dynamic;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;

import java.util.List;

public interface BrainHandle<T extends LivingEntity> {

    List<MemoryModuleType<?>> memoryModules();

    List<SensorType<? extends Sensor<? super T>>> sensors();

    default Brain<T> deserialize(Dynamic<?> dynamic) {
        var profile = Brain.createProfile(memoryModules(), sensors());
        return profile.deserialize(dynamic);
    }
}
