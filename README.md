# LibrGetter
A fabric mod which allows you to bruteforce
a librarian villager for the desired enchantment

# Installation
Right now, download the source code and run `./gradlew build`.
The compiled jar should be under build/libs/

# Usage
1. Face a librarian and type `/librget` (client-side command)
2. Face his lectern and type `/librget` once again
3. Now make sure, that there is a block underneath the lectern
and that you are able to pick up the lectern item once it breaks
4. Type `/librget <desired enchantment> <desired level>`
5. If you wish to stop, type `/librget stop`

If anything is not set, the mod should complain. Although, right now it does
not check whether the villager can upgrade his offers and whether the enchantment
may be obtained. This will be added soon.