#!/usr/bin/env python3
"""
Стартовые словари Lingvo World для абсолютных новичков.

  python3 tools/make_starters.py      # пишет app/src/main/assets/starters/<язык>-<перевод>.json

Принципы: простые частотные слова, от простого к сложному, небольшие тематические блоки
(первая порция — блоки 1–3, около 28 слов), примеры — только у глаголов (позднее в курсе).
Строка данных: "слово | варианты через запятую | дальше поля: /IPA/ или (для языков с roman=True) первое поле —
транскрипция; поле с «=» — свой перевод вместо общего; остальное — грамматическая заметка". None — понятия нет в языке (например, «она» в турецком совпадает с «он»).
"""
import json
import os
import re
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "starters")
CREATED = "2026-10-28T00:00:00Z"

BLOCKS = [
    (1, "Приветствия и вежливость"), (2, "Я, ты и вопросы"), (3, "Числа 1–10"), (4, "Семья и люди"),
    (5, "Дом и вещи"), (6, "Еда и напитки"), (7, "Главные глаголы"), (8, "Простые описания"),
]

# (русское значение, английское значение, часть речи, блок)
CONCEPTS = [
    ("привет", "hello", "phrase", 1), ("до свидания", "goodbye", "phrase", 1), ("да", "yes", "phrase", 1),
    ("нет", "no", "phrase", 1), ("пожалуйста", "please", "phrase", 1), ("спасибо", "thank you", "phrase", 1),
    ("извините", "sorry; excuse me", "phrase", 1), ("доброе утро", "good morning", "phrase", 1),
    ("я", "I", "pronoun", 2), ("ты", "you", "pronoun", 2), ("он", "he", "pronoun", 2), ("она", "she", "pronoun", 2),
    ("мы", "we", "pronoun", 2), ("кто?", "who?", "pronoun", 2), ("что?", "what?", "pronoun", 2),
    ("где?", "where?", "adverb", 2), ("когда?", "when?", "adverb", 2), ("как?", "how?", "adverb", 2),
    ("один", "one", "numeral", 3), ("два", "two", "numeral", 3), ("три", "three", "numeral", 3),
    ("четыре", "four", "numeral", 3), ("пять", "five", "numeral", 3), ("шесть", "six", "numeral", 3),
    ("семь", "seven", "numeral", 3), ("восемь", "eight", "numeral", 3), ("девять", "nine", "numeral", 3),
    ("десять", "ten", "numeral", 3),
    ("мама", "mother", "noun", 4), ("папа", "father", "noun", 4), ("брат", "brother", "noun", 4),
    ("сестра", "sister", "noun", 4), ("друг", "friend", "noun", 4), ("мужчина", "man", "noun", 4),
    ("женщина", "woman", "noun", 4), ("ребёнок", "child", "noun", 4),
    ("дом", "house; home", "noun", 5), ("дверь", "door", "noun", 5), ("стол", "table", "noun", 5),
    ("кровать", "bed", "noun", 5), ("книга", "book", "noun", 5), ("телефон", "phone", "noun", 5),
    ("окно", "window", "noun", 5),
    ("вода", "water", "noun", 6), ("хлеб", "bread", "noun", 6), ("кофе", "coffee", "noun", 6),
    ("чай", "tea", "noun", 6), ("молоко", "milk", "noun", 6), ("яблоко", "apple", "noun", 6),
    ("рыба", "fish", "noun", 6),
    ("быть", "to be", "verb", 7), ("иметь", "to have", "verb", 7), ("хотеть", "to want", "verb", 7),
    ("идти", "to go", "verb", 7), ("есть (кушать)", "to eat", "verb", 7), ("пить", "to drink", "verb", 7),
    ("говорить", "to speak", "verb", 7), ("знать", "to know", "verb", 7),
    ("большой", "big", "adjective", 8), ("маленький", "small", "adjective", 8), ("хороший", "good", "adjective", 8),
    ("плохой", "bad", "adjective", 8), ("новый", "new", "adjective", 8), ("горячий", "hot", "adjective", 8),
    ("холодный", "cold", "adjective", 8), ("красивый", "beautiful", "adjective", 8),
]
assert len(CONCEPTS) == 66

# Примеры к глаголам (блок 7): (пример, перевод)
RU_EX = ["Я студент.", "У меня есть кот.", "Я хочу воды.", "Я иду домой.", "Мы едим хлеб.", "Я пью чай.",
         "Он говорит медленно.", "Я не знаю."]

LANGS = {}

LANGS["en"] = dict(name="Английский", articles=set(), rows=[
    "hello||/həˈləʊ/", "goodbye|bye|/ɡʊdˈbaɪ/", "yes||/jes/", "no||/nəʊ/", "please||/pliːz/",
    "thank you|thanks|/ˈθæŋk juː/", "sorry|excuse me|/ˈsɒri/", "good morning||/ɡʊd ˈmɔːnɪŋ/",
    "I||/aɪ/", "you||/juː/", "he||/hiː/", "she||/ʃiː/", "we||/wiː/", "who||/huː/", "what||/wɒt/", "where||/weə/",
    "when||/wen/", "how||/haʊ/",
    "one||/wʌn/", "two||/tuː/", "three||/θriː/", "four||/fɔː/", "five||/faɪv/", "six||/sɪks/", "seven||/ˈsevn/",
    "eight||/eɪt/", "nine||/naɪn/", "ten||/ten/",
    "mother|mom,mum|/ˈmʌðə/", "father|dad|/ˈfɑːðə/", "brother||/ˈbrʌðə/", "sister||/ˈsɪstə/", "friend||/frend/",
    "man||/mæn/", "woman||/ˈwʊmən/", "child|kid|/tʃaɪld/",
    "house|home|/haʊs/", "door||/dɔː/", "table||/ˈteɪbl/", "bed||/bed/", "book||/bʊk/", "phone|telephone|/fəʊn/",
    "window||/ˈwɪndəʊ/",
    "water||/ˈwɔːtə/", "bread||/bred/", "coffee||/ˈkɒfi/", "tea||/tiː/", "milk||/mɪlk/", "apple||/ˈæpl/", "fish||/fɪʃ/",
    "to be|be|/biː/|I am, you are, he is", "to have|have|/hæv/|he has", "to want|want|/wɒnt/", "to go|go|/ɡəʊ/",
    "to eat|eat|/iːt/", "to drink|drink|/drɪŋk/", "to speak|speak,talk,to talk|/spiːk/", "to know|know|/nəʊ/",
    "big|large|/bɪɡ/", "small|little|/smɔːl/", "good||/ɡʊd/", "bad||/bæd/", "new||/njuː/", "hot||/hɒt/",
    "cold||/kəʊld/", "beautiful|pretty|/ˈbjuːtɪfl/",
], examples=["I am a student.", "I have a cat.", "I want some water.", "I am going home.", "We eat bread.",
             "I drink tea.", "He speaks slowly.", "I don't know."])

LANGS["de"] = dict(name="Немецкий", articles={"der", "die", "das"}, rows=[
    "Hallo", "Tschüss|Auf Wiedersehen", "ja", "nein", "bitte", "danke|danke schön", "Entschuldigung",
    "Guten Morgen",
    "ich", "du", "er", "sie||||sie также «они», а Sie — вежливое «Вы»", "wir", "wer", "was", "wo", "wann", "wie",
    "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn",
    "die Mutter|Mama||мн. ч. die Mütter", "der Vater|Papa||мн. ч. die Väter", "der Bruder||мн. ч. die Brüder",
    "die Schwester||мн. ч. die Schwestern", "der Freund||мн. ч. die Freunde; подруга — die Freundin",
    "der Mann||мн. ч. die Männer", "die Frau||мн. ч. die Frauen", "das Kind||мн. ч. die Kinder",
    "das Haus||мн. ч. die Häuser; дома — zu Hause", "die Tür||мн. ч. die Türen", "der Tisch||мн. ч. die Tische",
    "das Bett||мн. ч. die Betten", "das Buch||мн. ч. die Bücher", "das Handy|das Telefon|мобильный телефон",
    "das Fenster||мн. ч. die Fenster",
    "das Wasser", "das Brot", "der Kaffee", "der Tee", "die Milch", "der Apfel||мн. ч. die Äpfel", "der Fisch",
    "sein||ich bin, du bist, er ist", "haben||ich habe, du hast, er hat", "wollen||ich will, du willst",
    "gehen", "essen||du isst, er isst", "trinken", "sprechen||du sprichst, er spricht",
    "wissen||ich weiß, du weißt",
    "groß", "klein", "gut", "schlecht", "neu", "heiß", "kalt", "schön",
], examples=["Ich bin Student.", "Ich habe eine Katze.", "Ich will Wasser.", "Ich gehe nach Hause.",
             "Wir essen Brot.", "Ich trinke Tee.", "Er spricht langsam.", "Ich weiß es nicht."],
    ex_ru={1: "У меня есть кошка."})

LANGS["es"] = dict(name="Испанский", articles={"el", "la", "los", "las"}, rows=[
    "hola", "adiós", "sí", "no", "por favor", "gracias", "perdón|disculpe,lo siento", "buenos días",
    "yo", "tú", "él", "ella", "nosotros|nosotras", "¿quién?|quién", "¿qué?|qué", "¿dónde?|dónde",
    "¿cuándo?|cuándo", "¿cómo?|cómo",
    "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
    "la madre|mamá", "el padre|papá", "el hermano", "la hermana", "el amigo|la amiga", "el hombre", "la mujer",
    "el niño|la niña",
    "la casa", "la puerta", "la mesa", "la cama", "el libro", "el teléfono|el móvil", "la ventana",
    "el agua||женский род, но с артиклем el: el agua fría", "el pan", "el café", "el té", "la leche", "la manzana",
    "el pescado||рыба как еда; живая рыба — el pez",
    "ser|estar||ser — постоянное (soy estudiante), estar — состояние и место (estoy en casa)",
    "tener||tengo, tienes, tiene", "querer||quiero, quieres", "ir||voy, vas, va", "comer", "beber|tomar",
    "hablar", "saber||sé, sabes",
    "grande", "pequeño", "bueno", "malo", "nuevo", "caliente", "frío", "bonito|hermoso",
], examples=["Soy estudiante.", "Tengo un gato.", "Quiero agua.", "Voy a casa.", "Comemos pan.", "Bebo té.",
             "Él habla despacio.", "No sé."])

LANGS["fr"] = dict(name="Французский", articles={"le", "la", "les", "l'"}, rows=[
    "salut|bonjour||неформально", "au revoir", "oui", "non", "s'il vous plaît|s'il te plaît", "merci",
    "pardon|excusez-moi", "bonjour||днём тоже bonjour",
    "je", "tu", "il", "elle", "nous", "qui ?|qui", "quoi ?|quoi,que", "où ?|où", "quand ?|quand",
    "comment ?|comment",
    "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix",
    "la mère|maman", "le père|papa", "le frère", "la sœur|la soeur", "l'ami|l'amie||мужской род; подруга — l'amie",
    "l'homme||мужской род", "la femme", "l'enfant||мужской или женский род",
    "la maison", "la porte", "la table", "le lit", "le livre", "le téléphone|le portable", "la fenêtre",
    "l'eau||женский род", "le pain", "le café", "le thé", "le lait", "la pomme", "le poisson",
    "être||je suis, tu es, il est", "avoir||j'ai, tu as, il a", "vouloir||je veux", "aller||je vais, tu vas, il va",
    "manger", "boire||je bois", "parler", "savoir||je sais",
    "grand", "petit", "bon", "mauvais", "nouveau", "chaud", "froid", "beau|joli",
], examples=["Je suis étudiant.", "J'ai un chat.", "Je veux de l'eau.", "Je vais à la maison.",
             "Nous mangeons du pain.", "Je bois du thé.", "Il parle lentement.", "Je ne sais pas."])

LANGS["it"] = dict(name="Итальянский", articles={"il", "lo", "la", "i", "gli", "le", "l'"}, rows=[
    "ciao", "arrivederci", "sì", "no", "per favore|per piacere", "grazie", "scusi|scusa,mi scusi", "buongiorno",
    "io", "tu", "lui", "lei", "noi", "chi?|chi", "che cosa?|cosa,che", "dove?|dove", "quando?|quando", "come?|come",
    "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci",
    "la madre|mamma", "il padre|papà", "il fratello", "la sorella", "l'amico|l'amica||мужской род; подруга — l'amica",
    "l'uomo||мужской род", "la donna", "il bambino|la bambina",
    "la casa", "la porta", "il tavolo", "il letto", "il libro", "il telefono|il cellulare", "la finestra",
    "l'acqua||женский род", "il pane", "il caffè", "il tè", "il latte", "la mela", "il pesce",
    "essere||sono, sei, è", "avere||ho, hai, ha", "volere||voglio", "andare||vado, vai, va", "mangiare", "bere",
    "parlare", "sapere||so, sai",
    "grande", "piccolo", "buono", "cattivo|brutto", "nuovo", "caldo", "freddo", "bello",
], examples=["Sono uno studente.", "Ho un gatto.", "Voglio dell'acqua.", "Vado a casa.", "Mangiamo il pane.",
             "Bevo il tè.", "Lui parla lentamente.", "Non lo so."])

LANGS["pt"] = dict(name="Португальский", articles={"o", "a", "os", "as"}, rows=[
    "olá|oi", "adeus|tchau", "sim", "não", "por favor", "obrigado|obrigada||говорит мужчина; женщина — obrigada",
    "desculpe|com licença", "bom dia",
    "eu", "tu|você||в Бразилии обычно você", "ele", "ela", "nós", "quem?|quem", "o quê?|o que,que",
    "onde?|onde", "quando?|quando", "como?|como",
    "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez",
    "a mãe|mamãe", "o pai|papai", "o irmão", "a irmã", "o amigo|a amiga", "o homem", "a mulher", "a criança",
    "a casa", "a porta", "a mesa", "a cama", "o livro", "o telemóvel|o celular,o telefone||в Бразилии — o celular",
    "a janela",
    "a água", "o pão", "o café", "o chá", "o leite", "a maçã", "o peixe",
    "ser|estar||ser — постоянное, estar — состояние и место", "ter||tenho, tens, tem", "querer||quero",
    "ir||vou, vais, vai", "comer", "beber", "falar", "saber||sei",
    "grande", "pequeno", "bom", "mau|ruim||в Бразилии чаще ruim", "novo", "quente", "frio", "bonito",
], examples=["Eu sou estudante.", "Eu tenho um gato.", "Eu quero água.", "Eu vou para casa.", "Nós comemos pão.",
             "Eu bebo chá.", "Ele fala devagar.", "Eu não sei."])

LANGS["nl"] = dict(name="Нидерландский", articles={"de", "het"}, rows=[
    "hallo|hoi", "tot ziens|doei", "ja", "nee", "alstublieft|alsjeblieft", "dank je|dank u,bedankt",
    "sorry|pardon", "goedemorgen",
    "ik", "jij|je", "hij", "zij|ze", "wij|we", "wie?|wie", "wat?|wat", "waar?|waar", "wanneer?|wanneer", "hoe?|hoe",
    "één|een", "twee", "drie", "vier", "vijf", "zes", "zeven", "acht", "negen", "tien",
    "de moeder|mama", "de vader|papa", "de broer", "de zus", "de vriend|de vriendin", "de man", "de vrouw",
    "het kind",
    "het huis", "de deur", "de tafel", "het bed", "het boek", "de telefoon", "het raam",
    "het water", "het brood", "de koffie", "de thee", "de melk", "de appel", "de vis",
    "zijn||ik ben, jij bent, hij is", "hebben||ik heb, hij heeft", "willen||ik wil", "gaan||ik ga", "eten",
    "drinken", "spreken|praten", "weten||ik weet",
    "groot", "klein", "goed", "slecht", "nieuw", "heet", "koud", "mooi",
], examples=["Ik ben student.", "Ik heb een kat.", "Ik wil water.", "Ik ga naar huis.", "Wij eten brood.",
             "Ik drink thee.", "Hij spreekt langzaam.", "Ik weet het niet."])

LANGS["pl"] = dict(name="Польский", articles=set(), rows=[
    "cześć", "do widzenia", "tak", "nie", "proszę", "dziękuję", "przepraszam", "dzień dobry||и «доброе утро», и «добрый день»",
    "ja", "ty", "on", "ona", "my", "kto?|kto", "co?|co", "gdzie?|gdzie", "kiedy?|kiedy", "jak?|jak",
    "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć", "dziesięć",
    "mama|matka||ж. р.", "tata|ojciec||м. р.", "brat||м. р.", "siostra||ж. р.", "przyjaciel|kolega||м. р.",
    "mężczyzna||м. р.", "kobieta||ж. р.", "dziecko||ср. р.",
    "dom||м. р.", "drzwi||только мн. ч.", "stół||м. р.", "łóżko||ср. р.", "książka||ж. р.", "telefon||м. р.",
    "okno||ср. р.",
    "woda||ж. р.", "chleb||м. р.", "kawa||ж. р.", "herbata||ж. р.", "mleko||ср. р.", "jabłko||ср. р.", "ryba||ж. р.",
    "być||jestem, jesteś, jest", "mieć||mam, masz, ma", "chcieć||chcę, chcesz", "iść||idę, idziesz", "jeść||jem, jesz",
    "pić||piję, pijesz", "mówić||mówię, mówisz", "wiedzieć||wiem, wiesz",
    "duży", "mały", "dobry", "zły", "nowy", "gorący", "zimny", "piękny|ładny",
], examples=["Jestem studentem.", "Mam kota.", "Chcę wody.", "Idę do domu.", "Jemy chleb.", "Piję herbatę.",
             "On mówi powoli.", "Nie wiem."])

LANGS["tr"] = dict(name="Турецкий", articles=set(), rows=[
    "merhaba", "hoşça kal|güle güle||güle güle говорит тот, кто остаётся", "evet", "hayır", "lütfen",
    "teşekkürler|teşekkür ederim,sağ ol", "özür dilerim|pardon", "günaydın",
    "ben", "sen", "o||||=он, она, оно (в турецком нет рода)", None, "biz", "kim?|kim", "ne?|ne", "nerede?|nerede",
    "ne zaman?|ne zaman", "nasıl?|nasıl",
    "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz", "on",
    "anne", "baba", "erkek kardeş|ağabey||старший брат — ağabey", "kız kardeş|abla||старшая сестра — abla",
    "arkadaş", "adam|erkek", "kadın", "çocuk",
    "ev", "kapı", "masa", "yatak", "kitap", "telefon", "pencere",
    "su", "ekmek", "kahve", "çay", "süt", "elma", "balık",
    "olmak||в настоящем времени — окончание: öğrenciyim «я студент»",
    "var||«У меня есть …» — Benim … var||=есть, имеется (иметь)", "istemek", "gitmek", "yemek||также «еда»",
    "içmek", "konuşmak", "bilmek",
    "büyük", "küçük", "iyi", "kötü", "yeni", "sıcak", "soğuk", "güzel",
], examples=["Ben öğrenciyim.", "Bir kedim var.", "Su istiyorum.", "Eve gidiyorum.", "Ekmek yiyoruz.",
             "Çay içiyorum.", "O yavaş konuşuyor.", "Bilmiyorum."])

LANGS["uk"] = dict(name="Украинский", articles=set(), rows=[
    "привіт", "до побачення", "так", "ні", "будь ласка", "дякую", "вибачте|перепрошую", "доброго ранку",
    "я", "ти", "він", "вона", "ми", "хто?|хто", "що?|що", "де?|де", "коли?|коли", "як?|як",
    "один", "два", "три", "чотири", "п'ять|п’ять", "шість", "сім", "вісім", "дев'ять|дев’ять", "десять",
    "мама|мати", "тато|батько", "брат", "сестра", "друг", "чоловік||также «муж»", "жінка||также «жена»", "дитина",
    "дім|будинок", "двері", "стіл", "ліжко", "книжка|книга", "телефон", "вікно",
    "вода", "хліб", "кава", "чай", "молоко", "яблуко", "риба",
    "бути", "мати||у мене є — «у меня есть»", "хотіти", "іти|йти", "їсти", "пити", "говорити|розмовляти", "знати",
    "великий", "маленький", "добрий|хороший", "поганий", "новий", "гарячий", "холодний", "красивий|гарний",
], examples=["Я студент.", "У мене є кіт.", "Я хочу води.", "Я йду додому.", "Ми їмо хліб.", "Я п'ю чай.",
             "Він говорить повільно.", "Я не знаю."])

LANGS["zh"] = dict(name="Китайский", articles=set(), rows=[
    "你好||nǐ hǎo", "再见||zàijiàn", "对|是|duì||=да, верно", "不|不是|bù||нет; «не являюсь» — 不是 bú shì",
    "请||qǐng", "谢谢||xièxie", "对不起|不好意思|duìbuqǐ", "早上好|早|zǎoshang hǎo",
    "我||wǒ", "你||nǐ", "他||tā", "她||tā||звучит так же, как 他", "我们||wǒmen", "谁||shéi", "什么||shénme",
    "哪里|哪儿|nǎlǐ", "什么时候||shénme shíhou", "怎么||zěnme",
    "一||yī", "二||èr", "三||sān", "四||sì", "五||wǔ", "六||liù", "七||qī", "八||bā", "九||jiǔ", "十||shí",
    "妈妈||māma", "爸爸||bàba", "哥哥|弟弟|gēge|младший брат — 弟弟 dìdi|=старший брат",
    "姐姐|妹妹|jiějie|младшая сестра — 妹妹 mèimei|=старшая сестра", "朋友||péngyou", "男人||nánrén", "女人||nǚrén",
    "孩子||háizi",
    "房子|家|fángzi||«дом, семья» — 家 jiā", "门||mén", "桌子||zhuōzi", "床||chuáng", "书||shū",
    "手机|电话|shǒujī||=мобильный телефон", "窗户||chuānghu",
    "水||shuǐ", "面包||miànbāo", "咖啡||kāfēi", "茶||chá", "牛奶||niúnǎi", "苹果||píngguǒ", "鱼||yú",
    "是||shì||связка «являться»: 我是学生", "有||yǒu", "想|要|xiǎng", "去||qù", "吃||chī", "喝||hē", "说||shuō",
    "知道||zhīdào",
    "大||dà", "小||xiǎo", "好||hǎo", "坏||huài", "新||xīn", "热||rè", "冷||lěng", "漂亮||piàoliang",
], examples=["我是学生。（Wǒ shì xuésheng.）", "我有一只猫。（Wǒ yǒu yì zhī māo.）", "我想喝水。（Wǒ xiǎng hē shuǐ.）",
             "我去学校。（Wǒ qù xuéxiào.）", "我们吃面包。（Wǒmen chī miànbāo.）", "我喝茶。（Wǒ hē chá.）",
             "他说得很慢。（Tā shuō de hěn màn.）", "我不知道。（Wǒ bù zhīdào.）"],
    ex_ru={2: "Я хочу пить воду.", 3: "Я иду в школу."}, roman=True)

LANGS["ja"] = dict(name="Японский", articles=set(), rows=[
    "こんにちは||konnichiwa", "さようなら|またね|sayōnara", "はい||hai", "いいえ||iie",
    "お願いします|ください|onegaishimasu||просьба; «дайте, пожалуйста» — ください kudasai",
    "ありがとう|ありがとうございます|arigatō", "すみません|ごめんなさい|sumimasen", "おはようございます|おはよう|ohayō gozaimasu",
    "私|わたし|watashi", "あなた||anata", "彼|かれ|kare", "彼女|かのじょ|kanojo", "私たち|わたしたち|watashitachi",
    "誰|だれ|dare", "何|なに,なん|nani", "どこ||doko", "いつ||itsu", "どう|どうやって|dō",
    "一|いち|ichi", "二|に|ni", "三|さん|san", "四|よん,し|yon", "五|ご|go", "六|ろく|roku", "七|なな,しち|nana",
    "八|はち|hachi", "九|きゅう,く|kyū", "十|じゅう|jū",
    "お母さん|おかあさん,母,はは|okāsan||о своей маме — 母 haha", "お父さん|おとうさん,父,ちち|otōsan||о своём папе — 父 chichi",
    "兄|あに|ani|младший брат — 弟 otōto|=старший брат", "姉|あね|ane|младшая сестра — 妹 imōto|=старшая сестра",
    "友達|ともだち|tomodachi", "男の人|おとこのひと|otoko no hito", "女の人|おんなのひと|onna no hito",
    "子供|こども|kodomo",
    "家|いえ,うち|ie", "ドア||doa", "机|つくえ,テーブル|tsukue||письменный стол; обеденный — テーブル", "ベッド||beddo",
    "本|ほん|hon", "電話|でんわ,スマホ|denwa", "窓|まど|mado",
    "水|みず|mizu", "パン||pan", "コーヒー||kōhī", "お茶|おちゃ|ocha", "牛乳|ぎゅうにゅう,ミルク|gyūnyū", "りんご|リンゴ|ringo",
    "魚|さかな|sakana",
    "です||desu||вежливая связка: 学生です — «(я) студент»", "ある|あります|aru||«есть, имеется» (о предметах; о живых — いる)",
    "欲しい|ほしい|hoshii||«хочу (вещь)» — прилагательное; «хочу сделать» — 〜たい",
    "行く|いく,行きます|iku", "食べる|たべる,食べます|taberu", "飲む|のむ,飲みます|nomu", "話す|はなす,話します|hanasu",
    "知る|しる,知っています|shiru||«знаю» — 知っています",
    "大きい|おおきい|ōkii", "小さい|ちいさい|chiisai", "いい|良い,よい|ii", "悪い|わるい|warui",
    "新しい|あたらしい|atarashii", "熱い|あつい|atsui", "冷たい|つめたい,寒い|tsumetai||о погоде — 寒い samui",
    "きれい|綺麗,美しい|kirei",
], examples=["私は学生です。（Watashi wa gakusei desu.）", "時間があります。（Jikan ga arimasu.）",
             "水が欲しいです。（Mizu ga hoshii desu.）", "学校に行きます。（Gakkō ni ikimasu.）",
             "パンを食べます。（Pan o tabemasu.）", "お茶を飲みます。（Ocha o nomimasu.）",
             "彼はゆっくり話します。（Kare wa yukkuri hanashimasu.）", "知りません。（Shirimasen.）"],
    ex_ru={1: "У меня есть время.", 3: "Я иду в школу.", 4: "Я ем хлеб."}, roman=True)

LANGS["ko"] = dict(name="Корейский", articles=set(), rows=[
    "안녕하세요|안녕|annyeonghaseyo", "안녕히 가세요|안녕히 계세요|annyeonghi gaseyo||уходящему; остающемуся — 안녕히 계세요",
    "네|예|ne", "아니요||aniyo", "주세요||juseyo||«дайте, пожалуйста» (после названия вещи)",
    "감사합니다|고마워요|gamsahamnida", "죄송합니다|미안해요|joesonghamnida", "좋은 아침||joeun achim||дословно; чаще говорят 안녕하세요",
    "저|나|jeo||вежливо; неформально — 나 na", "너||neo||только с близкими", "그||geu", "그녀||geunyeo", "우리||uri",
    "누구||nugu", "뭐|무엇|mwo", "어디||eodi", "언제||eonje", "어떻게||eotteoke",
    "일|하나|il||китайско-корейский счёт; собственный — 하나 hana", "이|둘|i", "삼|셋|sam", "사|넷|sa", "오|다섯|o",
    "육|여섯|yuk", "칠|일곱|chil", "팔|여덟|pal", "구|아홉|gu", "십|열|sip",
    "엄마|어머니|eomma", "아빠|아버지|appa", "형|오빠|hyeong||старший брат (говорит мужчина); у девушки — 오빠 oppa|=старший брат",
    "언니|누나|eonni||старшая сестра (говорит девушка); у парня — 누나 nuna|=старшая сестра", "친구||chingu", "남자||namja",
    "여자||yeoja", "아이||ai",
    "집||jip", "문||mun", "책상|테이블|chaeksang", "침대||chimdae", "책||chaek", "핸드폰|전화|haendeupon", "창문||changmun",
    "물||mul", "빵||ppang", "커피||keopi", "차||cha", "우유||uyu", "사과||sagwa", "생선|물고기|saengseon||как еда; живая — 물고기",
    "이다||ida||связка: 학생이에요 — «(я) студент»", "있다|있어요|itda||=есть, имеется", "원하다|-고 싶다|wonhada||«хочу сделать» — -고 싶어요",
    "가다|가요|gada", "먹다|먹어요|meokda", "마시다|마셔요|masida", "말하다|말해요|malhada", "알다|알아요|alda",
    "크다||keuda", "작다||jakda", "좋다||jota", "나쁘다||nappeuda", "새롭다|새|saeropda", "뜨겁다||tteugeopda",
    "차갑다|춥다|chagapda||о погоде — 춥다", "예쁘다|아름답다|yeppeuda",
], examples=["저는 학생이에요. (Jeoneun haksaeng-ieyo.)", "고양이가 있어요. (Goyang-iga isseoyo.)",
             "물 마시고 싶어요. (Mul masigo sipeoyo.)", "학교에 가요. (Hakgyoe gayo.)", "빵을 먹어요. (Ppang-eul meogeoyo.)",
             "차를 마셔요. (Chareul masyeoyo.)", "그는 천천히 말해요. (Geuneun cheoncheonhi malhaeyo.)",
             "몰라요. (Mollayo.)"],
    ex_ru={2: "Я хочу пить воду.", 3: "Я иду в школу.", 4: "Я ем хлеб."}, roman=True)

LANGS["ar"] = dict(name="Арабский", articles=set(), rows=[
    "مرحبا||marḥaban", "مع السلامة||maʿa s-salāma", "نعم||naʿam", "لا||lā", "من فضلك||min faḍlik",
    "شكرا|شكراً|shukran", "آسف|آسفة|āsif||говорит мужчина; женщина — آسفة āsifa", "صباح الخير||ṣabāḥ al-khayr",
    "أنا||anā", "أنت|أنتِ|anta||к мужчине; к женщине — أنتِ anti", "هو||huwa", "هي||hiya", "نحن||naḥnu",
    "من||man", "ماذا|ما|mādhā", "أين||ayna", "متى||matā", "كيف||kayfa",
    "واحد||wāḥid", "اثنان||ithnān", "ثلاثة||thalātha", "أربعة||arbaʿa", "خمسة||khamsa", "ستة||sitta", "سبعة||sabʿa",
    "ثمانية||thamāniya", "تسعة||tisʿa", "عشرة||ʿashara",
    "أم|ماما|umm", "أب|بابا|ab", "أخ||akh", "أخت||ukht", "صديق||ṣadīq", "رجل||rajul", "امرأة||imraʾa", "طفل||ṭifl",
    "بيت|منزل|bayt", "باب||bāb", "طاولة||ṭāwila", "سرير||sarīr", "كتاب||kitāb", "هاتف|جوال|hātif", "نافذة||nāfidha",
    "ماء||māʾ", "خبز||khubz", "قهوة||qahwa", "شاي||shāy", "حليب||ḥalīb", "تفاحة||tuffāḥa", "سمك||samak",
    "كان||kāna||«был»; в настоящем времени связка опускается: أنا طالب — я студент",
    "عندي||ʿindī||«у меня есть»; дословно «у меня»|=у меня есть (иметь)", "أراد|أريد|arāda||«я хочу» — أريد urīdu",
    "ذهب|أذهب|dhahaba", "أكل|آكل|akala", "شرب|أشرب|shariba", "تكلم|أتكلم|takallama", "عرف|أعرف|ʿarafa",
    "كبير||kabīr", "صغير||ṣaghīr", "جيد||jayyid", "سيئ||sayyiʾ", "جديد||jadīd", "حار|ساخن|ḥārr", "بارد||bārid",
    "جميل||jamīl",
], examples=["أنا طالب. (anā ṭālib)", "عندي قطة. (ʿindī qiṭṭa)", "أريد ماء. (urīdu māʾ)",
             "أذهب إلى البيت. (adhhabu ilā l-bayt)", "نأكل الخبز. (naʾkulu l-khubz)", "أشرب الشاي. (ashrabu sh-shāy)",
             "هو يتكلم ببطء. (huwa yatakallamu bi-buṭʾ)", "لا أعرف. (lā aʿrif)"],
    ex_ru={1: "У меня есть кошка."}, roman=True)

# Русский для тех, кто переводит на английский (курс ru → en).
LANGS["ru"] = dict(name="Русский", articles=set(), translation="en", rows=[
    "привет||privét", "до свидания|пока|do svidániya", "да||da", "нет||net", "пожалуйста||pozhálujsta",
    "спасибо||spasíbo", "извините|простите|izviníte", "доброе утро||dóbroye útro",
    "я||ya", "ты||ty", "он||on", "она||oná", "мы||my", "кто?|кто|kto", "что?|что|chto", "где?|где|gde",
    "когда?|когда|kogdá", "как?|как|kak",
    "один||odín", "два||dva", "три||tri", "четыре||chetýre", "пять||pyat'", "шесть||shest'", "семь||sem'",
    "восемь||vósem'", "девять||dévyat'", "десять||désyat'",
    "мама|мать|máma", "папа|отец|pápa", "брат||brat", "сестра||sestrá", "друг||drug", "мужчина||muzhchína",
    "женщина||zhénshchina", "ребёнок|ребенок|rebyónok",
    "дом||dom", "дверь||dver'", "стол||stol", "кровать||krovát'", "книга||kníga", "телефон||telefón", "окно||oknó",
    "вода||vodá", "хлеб||khleb", "кофе||kófe||мужской род: чёрный кофе", "чай||chay", "молоко||molokó",
    "яблоко||yábloko", "рыба||rýba",
    "быть||byt'||в настоящем времени обычно опускается: я студент", "иметь||imét'||обычно говорят «у меня есть»",
    "хотеть||khotét'", "идти||idtí", "есть||yest'", "пить||pit'", "говорить||govorít'", "знать||znat'",
    "большой||bol'shóy", "маленький||málen'kiy", "хороший||khoróshiy", "плохой||plokhóy", "новый||nóvyy",
    "горячий||goryáchiy", "холодный||kholódnyy", "красивый||krasívyy",
], examples=RU_EX, ex_tr=["I am a student.", "I have a cat.", "I want some water.", "I am going home.",
                          "We eat bread.", "I drink tea.", "He speaks slowly.", "I don't know."])

LEGACY_TERM = {"es": "spanish", "de": "german"}


def strip_marks(s):
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return re.sub(r"[ʿʾ'’]", "", s)


def build(code, info):
    tr = info.get("translation", "ru")
    term_key = LEGACY_TERM.get(code, "term")
    tr_key = "russian" if tr == "ru" else "translation"
    ex_key = "example_" + code
    ex_tr_key = "example_ru" if tr == "ru" else "example_translation"
    entries = []
    verb_i = 0
    for i, (ru, en, pos, block) in enumerate(CONCEPTS):
        row = info["rows"][i]
        if pos == "verb":
            verb_i += 1
        if row is None:
            continue
        parts = [p.strip() for p in row.split("|")]
        term, alts = parts[0], (parts[1] if len(parts) > 1 else "")
        trans, own, notes = "", "", []
        for k, f in enumerate(parts[2:]):
            if not f:
                continue
            if f.startswith("="):
                own = f[1:].strip()
            elif f.startswith("/") or (k == 0 and info.get("roman")):
                trans = f
            else:
                notes.append(f)
        note = "; ".join(notes)
        alts = [a.strip() for a in alts.split(",") if a.strip()] if alts else []
        meaning = own or (ru if tr == "ru" else en)
        e = {"id": f"start-{code}-{i + 1:03d}", term_key: term, tr_key: meaning}
        art = ""
        if info["articles"]:
            first = term.split(" ")[0].lower()
            if term.lower().startswith("l'"):
                art = "l'"
            elif first in info["articles"] and " " in term and pos == "noun":
                art = first
        if info["articles"]:
            e["article"] = art
        e["part_of_speech"] = pos
        if trans:
            e["transcription"] = trans
        if info.get("roman") and trans:
            # Латиница без диакритики — чтобы в письменном режиме можно было ответить с обычной клавиатуры.
            plain = strip_marks(trans).lower()
            for v in (plain, plain.replace(" ", "")):
                if v and v not in alts:
                    alts.append(v)
        if alts:
            e["alternatives"] = alts
        if note:
            e["grammar_notes"] = note
        if code != "en":
            # Английская подсказка для локальной нейросети, рисующей картинки к словам.
            e["gloss_en"] = en.split(";")[0].replace("?", "").strip()
        if pos == "verb":
            k = verb_i - 1
            e[ex_key] = info["examples"][k]
            if tr == "ru":
                e[ex_tr_key] = info.get("ex_ru", {}).get(k, RU_EX[k])
            else:
                e[ex_tr_key] = info["ex_tr"][k]
        e.update({
            "level": "A1", "tags": [BLOCKS[block - 1][1]], "notes": "", "source": "Стартовый словарь",
            "block": block, "block_title": BLOCKS[block - 1][1],
            "created_at": CREATED, "updated_at": CREATED,
        })
        entries.append(e)
    doc = {"schema_version": 1, "language": code, "updated_at": CREATED, "starter": True, "entries": entries}
    if tr != "ru":
        doc["translation_language"] = tr
    return tr, doc


def main():
    os.makedirs(OUT, exist_ok=True)
    for code, info in LANGS.items():
        assert len(info["rows"]) == 66, code
        assert len(info["examples"]) == 8, code
        tr, doc = build(code, info)
        terms = [e.get(LEGACY_TERM.get(code, "term")) for e in doc["entries"]]
        assert len(terms) == len(set(terms)), (code, [t for t in terms if terms.count(t) > 1])
        with open(os.path.join(OUT, f"{code}-{tr}.json"), "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False, indent=1)
            f.write("\n")
        print(f"{code}-{tr}: {len(doc['entries'])} слов")


if __name__ == "__main__":
    main()
