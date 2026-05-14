package tn.uib.bnpl.gestion_utilisateur.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.uib.bnpl.gestion_utilisateur.classes.Banque;
import tn.uib.bnpl.gestion_utilisateur.repository.BanqueRepository;
import tn.uib.bnpl.gestion_utilisateur.services.BanqueService;

@RestController
@RequestMapping("/api/banques")
public class BanqueController {

    private final BanqueService banqueService;

    public BanqueController(BanqueService banqueService) {
        this.banqueService = banqueService;
    }

    @GetMapping
    public List<Banque> getAll() {
        return banqueService.getAll();
    }

    @PostMapping
    public Banque create(@RequestBody Banque banque) {
        return banqueService.create(banque);
    }
}