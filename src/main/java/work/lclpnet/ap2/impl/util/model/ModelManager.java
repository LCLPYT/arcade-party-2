package work.lclpnet.ap2.impl.util.model;

import net.minecraft.util.Identifier;
import work.lclpnet.ap2.impl.util.FunctionExecutor;

import java.util.Optional;

public class ModelManager {

    private final FunctionExecutor functionExecutor;

    public ModelManager(FunctionExecutor functionExecutor) {
        this.functionExecutor = functionExecutor;
    }

    public Optional<FunctionModel> loadFunction(Identifier id) {
        return functionExecutor.getFunction(id).map(fun -> new FunctionModel(functionExecutor, fun));
    }
}
