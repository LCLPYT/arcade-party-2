package work.lclpnet.ap2.impl.game.data;

import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.data.SubjectRefFactory;

import java.util.Optional;

public abstract class BaseDataContainer<T, Ref extends SubjectRef> implements DataContainer<T, Ref> {

    protected final SubjectRefFactory<T, Ref> refs;

    public BaseDataContainer(SubjectRefFactory<T, Ref> refs) {
        this.refs = refs;
    }

    @Override
    public Optional<DataEntry<Ref>> getEntry(T subject) {
        return getEntry(refs.create(subject));
    }
}
