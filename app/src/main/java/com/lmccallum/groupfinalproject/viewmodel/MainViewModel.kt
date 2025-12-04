package com.lmccallum.groupfinalproject.viewmodel

import androidx.lifecycle.ViewModel
import com.lmccallum.groupfinalproject.model.TeamMember


//View model to display our team member and what the app does
class MainViewModel : ViewModel() {
    val teamMembers = listOf(
        TeamMember("Spencer Martin", "1040415", "Section 2"),
        TeamMember("William Mouhtouris", "0616723", "Section 2"),
        TeamMember("Logan McCallum", "1152955", "Section 2"),
        TeamMember("Adam O'Neil", "1051969", "Section 2"),
        TeamMember("Cameron Nold", "1146436", "Section 2")
    )

    val appDescription = "Magic: The Gathering Card Scanner/Searcher/Translator - " +
                         "Scan and translate MTG cards in foreign languages or search cards" +
                         "in english"
}