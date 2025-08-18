package work.lclpnet.ap2.impl.util.world;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.kibu.mc.KibuBlockEntity;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.mc.KibuBlockState;
import work.lclpnet.kibu.mc.KibuEntity;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.Collection;
import java.util.function.UnaryOperator;

public class MappedBlockStructure implements BlockStructure {

    private final BlockStructure parent;
    private final UnaryOperator<KibuBlockState> mapper;

    public MappedBlockStructure(BlockStructure parent, UnaryOperator<KibuBlockState> mapper) {
        this.parent = parent;
        this.mapper = mapper;
    }

    @Override
    public int getDataVersion() {
        return parent.getDataVersion();
    }

    @Override
    public KibuBlockPos getOrigin() {
        return parent.getOrigin();
    }

    @Override
    public int getWidth() {
        return parent.getWidth();
    }

    @Override
    public int getHeight() {
        return parent.getHeight();
    }

    @Override
    public int getLength() {
        return parent.getLength();
    }

    @Override
    public KibuBlockEntity getBlockEntity(KibuBlockPos pos) {
        return parent.getBlockEntity(pos);
    }

    @Override
    public void setBlockEntity(KibuBlockPos pos, KibuBlockEntity blockEntity) {
        parent.setBlockEntity(pos, blockEntity);
    }

    @Override
    public int getBlockEntityCount() {
        return parent.getBlockEntityCount();
    }

    @Override
    public void setBlockState(KibuBlockPos pos, KibuBlockState state) {
        parent.setBlockState(pos, mapper.apply(state));
    }

    @Override
    @NotNull
    public KibuBlockState getBlockState(KibuBlockPos pos) {
        return mapper.apply(parent.getBlockState(pos));
    }

    @Override
    public Iterable<KibuBlockPos> getBlockPositions() {
        return parent.getBlockPositions();
    }

    @Override
    public int getBlockCount() {
        return parent.getBlockCount();
    }

    @Override
    public boolean addEntity(KibuEntity entity) {
        return parent.addEntity(entity);
    }

    @Override
    public boolean removeEntity(KibuEntity entity) {
        return parent.removeEntity(entity);
    }

    @Override
    public Collection<? extends KibuEntity> getEntities() {
        return parent.getEntities();
    }
}
