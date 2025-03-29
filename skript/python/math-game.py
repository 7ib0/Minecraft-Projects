# Made by revqz and tibo (We cool) - Not nerds
import random
import tkinter as tk

difficulty = None
score = 0
rounds_played = 0
max_rounds = 10
timer_id = None
time_left = 120
current_answer = None

game_window = tk.Tk()
game_window.withdraw()
game_window.title("Math Game")
game_window.geometry("800x700")
game_window.configure(bg="#cce7ff")


question_label = tk.Label(game_window, text="", font=("Arial", 14))
question_label.pack(pady=10)

answer_entry = tk.Entry(game_window, font=("Arial", 14))
answer_entry.pack(pady=10)

feedback_label = tk.Label(game_window, text="", font=("Arial", 12))
feedback_label.pack(pady=5)

score_label = tk.Label(game_window, text="Score: 0", font=("Arial", 12))
score_label.pack(pady=5)

timer_label = tk.Label(game_window, text="Time left: 120", font=("Arial", 12))
timer_label.pack(pady=5)

button_frame = tk.Frame(game_window)
button_frame.pack(pady=10)

submit_btn = tk.Button(button_frame, text="Submit", command=lambda: check_answer())
submit_btn.pack(side="left", padx=10)

skip_btn = tk.Button(button_frame, text="Skip", command=lambda: skip_question())
skip_btn.pack(side="left", padx=10)

game_window.bind("<Return>", lambda event: check_answer())

end_screen = tk.Toplevel()
end_screen.withdraw()
end_screen.title("Game Over")
end_screen.geometry("400x200")

end_label = tk.Label(end_screen, text="", font=("Arial", 16))
end_label.pack(pady=20)

play_again_btn = tk.Button(end_screen, text="Play Again", font=("Arial", 12), command=lambda: restart_game())
play_again_btn.pack(pady=10)

window = tk.Tk()
window.title("Select Difficulty")
window.geometry("400x300")

def set_difficulty(level):
    global difficulty
    difficulty = level
    window.destroy()
    game_window.deiconify()
    start_game()

tk.Label(window, text="Choose difficulty:", font=("Comic Sans MS", 16, "bold"), bg="#cce7ff", fg="navy").pack(pady=10)
tk.Button(window, text="Easy", command=lambda: set_difficulty("easy"), bg="white", fg="green", font=("Arial", 12, "bold")).pack(pady=10)
tk.Button(window, text="Normal", command=lambda: set_difficulty("normal"), bg="white", fg="orange", font=("Arial", 12, "bold")).pack(pady=10)
tk.Button(window, text="Hard", command=lambda: set_difficulty("hard"), bg="white", fg="red", font=("Arial", 12, "bold")).pack(pady=10)

def generate_question():
    global current_answer, time_left
    time_left = 120
    update_timer()

    if difficulty == "easy":
        operations = ['+', '-']
        num1, num2 = random.randint(10, 50), random.randint(10, 50)
    elif difficulty == "normal":
        operations = ['+', '-', '*']
        num1, num2 = random.randint(20, 100), random.randint(20, 100)
    else:
        operations = ['+', '-', '*', '/']
        num2 = random.randint(2, 20)
        num1 = num2 * random.randint(10, 30)
    op = random.choice(operations)
    question = f"{num1} {op} {num2}"
    current_answer = eval(question)
    return question

def show_question():
    global rounds_played
    if rounds_played >= max_rounds:
        end_game()
        return
    question = generate_question()
    question_label.config(text=f"Question {rounds_played + 1}: {question}")
    feedback_label.config(text="")
    answer_entry.delete(0, tk.END)
    answer_entry.focus()

def check_answer():
    global score, timer_id, rounds_played
    user_answer = answer_entry.get()
    try:
        if float(user_answer) == round(current_answer, 2):
            feedback_label.config(text="Correct! ", fg="green")
            score += 1
            score_label.config(text=f"Score: {score}")
            rounds_played += 1
            if timer_id:
                game_window.after_cancel(timer_id)
            game_window.after(1000, show_question)
        else:
            feedback_label.config(text="Try again!", fg="red")
    except:
        feedback_label.config(text="Enter a number!", fg="orange")

def skip_question():
    global rounds_played, timer_id
    feedback_label.config(text="Skipped!", fg="blue")
    rounds_played += 1
    if timer_id:
        game_window.after_cancel(timer_id)
    game_window.after(1500, show_question)

def update_timer():
    global time_left, timer_id, rounds_played
    if time_left > 0:
        time_left -= 1
        timer_label.config(text=f"Time left: {time_left}")
        timer_id = game_window.after(1000, update_timer)
    else:
        feedback_label.config(text=f" Time's up!", fg="blue")
        rounds_played += 1
        game_window.after(2000, show_question)

def end_game():
    game_window.withdraw()
    end_label.config(text=f"Game Over! Your score: {score}/{max_rounds}")
    end_screen.deiconify()

def restart_game():
    global score, rounds_played
    end_screen.withdraw()
    score = 0
    rounds_played = 0
    score_label.config(text="Score: 0")
    game_window.deiconify()
    start_game()

def start_game():
    show_question()

window.mainloop()
game_window.mainloop()
