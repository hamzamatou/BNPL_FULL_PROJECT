package tn.uib.bnpl.gestion_utilisateur.services;

import org.springframework.stereotype.Service;
import tn.uib.bnpl.gestion_utilisateur.classes.Banque;
import tn.uib.bnpl.gestion_utilisateur.repository.BanqueRepository;

import java.util.List;

@Service
public class BanqueServiceImpl implements BanqueService {

    private final BanqueRepository banqueRepository;

    public BanqueServiceImpl(BanqueRepository banqueRepository) {
        this.banqueRepository = banqueRepository;
    }

    @Override
    public List<Banque> getAll() {
        return banqueRepository.findAll();
    }

    @Override
    public Banque create(Banque banque) {
        return banqueRepository.save(banque);
    }
}